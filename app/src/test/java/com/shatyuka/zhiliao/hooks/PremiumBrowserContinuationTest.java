package com.shatyuka.zhiliao.hooks;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class PremiumBrowserContinuationTest {
    private static final class Paging {
        final boolean isEnd;
        final String next;
        Paging(boolean isEnd, String next) { this.isEnd = isEnd; this.next = next; }
    }

    private static final class Response {
        List<String> data;
        final Paging paging;
        Response(List<String> data, boolean end, String next) { this(data, new Paging(end, next)); }
        Response(List<String> data, Paging paging) {
            this.data = data;
            this.paging = paging;
        }
        // Identity markers must never accidentally match a different, equal wrapper.
        @Override public boolean equals(Object ignored) { return true; }
        @Override public int hashCode() { return 0; }
    }

    private static final class ViewModel {
        boolean ready = true;
        boolean nextEnabled = true;
        boolean loading = true;
        Object cached;
    }

    private static final class Host implements PremiumBrowserContinuation.Host {
        final List<Object> initial = new ArrayList<>();
        final List<Paging> requests = new ArrayList<>();
        final List<Object> completed = new ArrayList<>();
        final List<String> order = new ArrayList<>();
        final List<Response> inlineResponses = new ArrayList<>();
        final PremiumBrowserContinuation helper = new PremiumBrowserContinuation(this);
        final ViewModel vm = new ViewModel();

        Response page(boolean end, String next, String... items) {
            Response response = new Response(Arrays.asList(items), end, next);
            List<String> kept = PremiumFilterPolicy.filter(response.data, "paid"::equals);
            if (kept != response.data) {
                response.data = kept;
                if (kept.isEmpty()) helper.markFilteredEmpty(response);
            }
            return response;
        }

        void complete(Paging requested, Response response) throws ReflectiveOperationException {
            // The unchanged native b$i$1 callback releases its request and completes first.
            completed.add(response);
            order.add("native completion");
            helper.afterLoadMore(vm, requested, response);
        }

        @Override public boolean empty(Object response) { return ((Response) response).data.isEmpty(); }
        @Override public Object paging(Object response) { return ((Response) response).paging; }
        @Override public boolean end(Object paging) { return ((Paging) paging).isEnd; }
        @Override public String next(Object paging) { return ((Paging) paging).next; }
        @Override public boolean zeroPageReady(Object viewModel) { return ((ViewModel) viewModel).ready; }
        @Override public boolean nextRenderEnabled(Object viewModel) { return ((ViewModel) viewModel).nextEnabled; }
        @Override public void cacheInitial(Object viewModel, Object response) {
            ((ViewModel) viewModel).cached = response;
            ((ViewModel) viewModel).loading = false;
        }
        @Override public void emitInitial(Object viewModel, Object response) {
            initial.add(response);
            order.add("native initial");
        }
        @Override public void request(Object viewModel, Object paging) throws ReflectiveOperationException {
            helper.beforeRequest(viewModel); // The real native VM.a(Paging) entry-point hook.
            requests.add((Paging) paging);
            order.add("request");
            if (!inlineResponses.isEmpty()) complete((Paging) paging, inlineResponses.remove(0));
        }
    }

    @Test public void allPaidPagesCompleteFirstAndStopAfterThreeWithoutChangingPaging() throws Exception {
        Host host = new Host();
        Response first = host.page(false, "cursor1", "paid", "paid");
        Paging originalPaging = first.paging;
        assertTrue(host.helper.beforeInitial(host.vm, first));
        assertSame(first, host.vm.cached);
        assertFalse(host.vm.loading);
        assertSame(first, host.initial.get(0));
        assertSame(originalPaging, host.requests.get(0));
        assertEquals(Arrays.asList("native initial", "request"), host.order);

        Response second = host.page(false, "cursor2", "paid");
        host.complete(first.paging, second);
        Response third = host.page(false, "cursor3", "paid");
        host.complete(second.paging, third);
        Response fourth = host.page(false, "cursor4", "paid");
        host.complete(third.paging, fourth);
        assertEquals(3, host.requests.size());
        assertEquals(Arrays.asList(second, third, fourth), host.completed);
        assertSame(fourth, host.completed.get(2));
        assertSame(originalPaging, first.paging);
        assertFalse(fourth.paging.isEnd);
        assertEquals("cursor4", fourth.paging.next);
        assertEquals(Arrays.asList("native initial", "request", "native completion", "request",
                "native completion", "request", "native completion"), host.order);

        // The user can still request the real fourth cursor after the automatic bound.
        host.request(host.vm, fourth.paging);
        Response fifth = host.page(false, "cursor5", "paid");
        host.complete(fourth.paging, fifth);
        assertEquals(5, host.requests.size()); // Three automatic, one manual, one new automatic.
        assertSame(fifth.paging, host.requests.get(4));
    }

    @Test public void ordinaryMixedAndRealEmptyCollectionsRetainNativeHandling() throws Exception {
        Host host = new Host();
        Response ordinary = host.page(false, "ordinary", "free", "article");
        assertFalse(host.helper.beforeInitial(host.vm, ordinary));
        assertNull(host.vm.cached);
        assertTrue(host.vm.loading);
        Response mixed = host.page(false, "mixed", "free", "paid", "article");
        assertEquals(Arrays.asList("free", "article"), mixed.data);
        assertFalse(host.helper.beforeInitial(host.vm, mixed));
        host.complete(ordinary.paging, mixed);
        Response realEmpty = host.page(false, "realEmpty");
        assertFalse(host.helper.beforeInitial(host.vm, realEmpty));
        host.complete(mixed.paging, realEmpty);
        assertTrue(host.initial.isEmpty());
        assertTrue(host.requests.isEmpty());
        assertSame(realEmpty, host.completed.get(1));
    }

    @Test public void cachedFirstPageWaitsForNativeZeroPageAndResumesOnlyOnce() throws Exception {
        Host host = new Host();
        host.vm.ready = false;
        Response response = host.page(false, "cursor", "paid");
        assertTrue(host.helper.beforeInitial(host.vm, response));
        assertSame(response, host.vm.cached);
        assertFalse(host.vm.loading);
        assertTrue(host.requests.isEmpty());
        assertTrue(host.initial.isEmpty());
        host.helper.afterZeroPage(host.vm, false); // Failed/empty native zero page did not become ready.
        assertTrue(host.requests.isEmpty());
        host.vm.ready = true;
        host.emitInitial(host.vm, host.vm.cached); // The original zero-page callback emits g(h).
        host.helper.afterZeroPage(host.vm, false);
        host.helper.afterZeroPage(host.vm, false);
        assertEquals(1, host.initial.size());
        assertEquals(1, host.requests.size());
        assertSame(response.paging, host.requests.get(0));
    }

    @Test public void realTerminalAndAbsentCursorsNeverTriggerFollowOn() throws Exception {
        for (String cursor : Arrays.asList("terminal-cursor", "", null)) {
            Host host = new Host();
            Response terminal = host.page(true, cursor, "paid");
            assertTrue(host.helper.beforeInitial(host.vm, terminal));
            assertSame(terminal, host.initial.get(0));
            assertTrue(terminal.paging.isEnd);
            assertEquals(cursor, terminal.paging.next);
            host.complete(new Paging(false, "previous"), terminal);
            assertTrue(host.requests.isEmpty());
        }
        for (String cursor : Arrays.asList("", null)) {
            Host host = new Host();
            Response noCursor = host.page(false, cursor, "paid");
            assertTrue(host.helper.beforeInitial(host.vm, noCursor));
            assertTrue(host.requests.isEmpty());
            assertFalse(noCursor.paging.isEnd);
        }
    }

    @Test public void repeatedAndCyclicCursorsStopWithoutFalseTerminalState() throws Exception {
        Host host = new Host();
        Response first = host.page(false, "cursor1", "paid");
        host.helper.beforeInitial(host.vm, first);
        Response repeated = host.page(false, "cursor1", "paid");
        host.complete(first.paging, repeated);
        assertEquals(1, host.requests.size());
        assertFalse(repeated.paging.isEnd);

        host = new Host();
        first = host.page(false, "cursor1", "paid");
        host.helper.beforeInitial(host.vm, first);
        Response second = host.page(false, "cursor2", "paid");
        host.complete(first.paging, second);
        Response cycle = host.page(false, "cursor1", "paid");
        host.complete(second.paging, cycle);
        assertEquals(2, host.requests.size());
        assertFalse(cycle.paging.isEnd);
        assertEquals("cursor1", cycle.paging.next);
    }

    @Test public void markersUseWrapperIdentityAndRecheckEmptyData() throws Exception {
        Host host = new Host();
        Response marked = host.page(false, "marked", "paid");
        Response equalButUnmarked = host.page(false, "unmarked");
        assertEquals(marked, equalButUnmarked);
        assertFalse(host.helper.beforeInitial(host.vm, equalButUnmarked));
        marked.data = Collections.singletonList("free");
        assertFalse(host.helper.beforeInitial(host.vm, marked));
        assertTrue(host.requests.isEmpty());
    }

    @Test public void nonemptyCompletionAndNewInitialFlowResetBudget() throws Exception {
        Host host = new Host();
        Response first = host.page(false, "cursor1", "paid");
        host.helper.beforeInitial(host.vm, first);
        Response second = host.page(false, "cursor2", "paid");
        host.complete(first.paging, second);
        Response third = host.page(false, "cursor3", "paid");
        host.complete(second.paging, third);
        Response nonempty = host.page(false, "cursor4", "free", "paid");
        host.complete(third.paging, nonempty);
        assertEquals(3, host.requests.size());
        assertEquals(Collections.singletonList("free"), nonempty.data);
        Response paidAgain = host.page(false, "cursor5", "paid");
        host.complete(nonempty.paging, paidAgain);
        assertEquals(4, host.requests.size());
        Response freshInitial = host.page(false, "cursor1", "paid");
        assertTrue(host.helper.beforeInitial(host.vm, freshInitial));
        assertEquals(5, host.requests.size());
        assertSame(freshInitial.paging, host.requests.get(4));
    }

    @Test public void synchronousNativeCallbacksAlsoRespectTheThreeRequestBound() throws Exception {
        Host host = new Host();
        Response first = host.page(false, "cursor1", "paid");
        for (int index = 2; index <= 5; index++)
            host.inlineResponses.add(host.page(false, "cursor" + index, "paid"));
        assertTrue(host.helper.beforeInitial(host.vm, first));
        assertEquals(3, host.requests.size());
        assertEquals(3, host.completed.size());
        assertEquals(1, host.inlineResponses.size());
        assertFalse(((Response) host.completed.get(2)).paging.isEnd);
    }

    @Test public void missingPagingPreservesCompletionWithoutAutomaticRequests() throws Exception {
        Host host = new Host();
        Response response = new Response(Collections.emptyList(), null);
        host.helper.markFilteredEmpty(response);
        assertTrue(host.helper.beforeInitial(host.vm, response));
        assertSame(response, host.initial.get(0));
        host.complete(new Paging(false, "request"), response);
        assertSame(response, host.completed.get(0));
        assertNull(response.paging);
        assertTrue(host.requests.isEmpty());
    }

    @Test public void deferredResumeHonorsNativeSkipAndDisabledRenderModes() throws Exception {
        Host host = new Host();
        host.vm.ready = false;
        Response response = host.page(false, "cursor", "paid");
        assertTrue(host.helper.beforeInitial(host.vm, response));
        host.vm.ready = true;
        host.helper.afterZeroPage(host.vm, true);
        assertTrue(host.requests.isEmpty());
        host.vm.nextEnabled = false;
        host.helper.afterZeroPage(host.vm, false);
        assertTrue(host.requests.isEmpty());
        assertSame(response, host.vm.cached);
        assertFalse(response.paging.isEnd);
        host.vm.nextEnabled = true;
        host.emitInitial(host.vm, host.vm.cached);
        host.helper.afterZeroPage(host.vm, false);
        assertEquals(1, host.requests.size());
        assertSame(response.paging, host.requests.get(0));
    }
}
