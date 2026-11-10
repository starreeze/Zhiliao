package com.shatyuka.zhiliao.hooks;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class PremiumListContinuationTest {
    private static final class Paging {
        final String next;
        final boolean end;
        Paging(String next, boolean end) { this.next = next; this.end = end; }
    }
    private static final class Response {
        Paging paging;
        List<String> data;
        Response(String next, boolean end, String... data) {
            paging = new Paging(next, end);
            this.data = Arrays.asList(data);
        }
        @Override public boolean equals(Object other) { return true; }
        @Override public int hashCode() { return 0; }
    }
    private static final class Fragment {
        final List<String> rows = new ArrayList<>();
        Paging paging;
        boolean alive = true;
    }
    private static final class Control {
        final Runnable action;
        boolean dismissed;
        Control(Runnable action) { this.action = action; }
    }
    private static final class Host implements PremiumListContinuation.Host {
        final PremiumListContinuation.Engine helper = new PremiumListContinuation.Engine(this);
        final Fragment fragment = new Fragment();
        final List<Paging> requests = new ArrayList<>();
        final List<Response> completions = new ArrayList<>();
        final List<String> order = new ArrayList<>();
        final List<Runnable> queued = new ArrayList<>();
        final List<Control> controls = new ArrayList<>();
        boolean delayed;
        boolean nativeRequestPosted;
        final List<Runnable> postedRequests = new ArrayList<>();

        Response filtered(String next, boolean end, String... items) {
            Response response = new Response(next, end, items);
            List<String> kept = PremiumFilterPolicy.filter(response.data, "paid"::equals);
            if (kept != response.data) {
                response.data = kept;
                if (kept.isEmpty()) helper.mark(response);
            }
            return response;
        }
        void complete(Response response, boolean refresh) throws ReflectiveOperationException {
            complete(fragment, response, refresh);
        }
        void complete(Fragment owner, Response response, boolean refresh) throws ReflectiveOperationException {
            // Emulate the unchanged host callback before entering the AFTER hook. Its native
            // empty branch inserts a placeholder and may discard the initial real paging.
            completions.add(response);
            order.add("native completion");
            if (refresh) owner.rows.clear();
            owner.rows.addAll(response.data);
            if (owner.rows.isEmpty()) owner.rows.add("native-empty");
            owner.paging = refresh && response.data.isEmpty() ? null : response.paging;
            helper.afterCompletion(owner, response, refresh);
        }
        void drain() { while (!queued.isEmpty()) queued.remove(0).run(); }
        @Override public void enqueue(Object fragment, Runnable action) {
            if (delayed) queued.add(action); else action.run();
        }
        @Override public void failure(Throwable failure) { throw new AssertionError(failure); }
        @Override public boolean active(Object fragment) { return ((Fragment) fragment).alive; }
        @Override public Object paging(Object response) { return ((Response) response).paging; }
        @Override public boolean end(Object paging) { return ((Paging) paging).end; }
        @Override public String next(Object paging) { return ((Paging) paging).next; }
        @Override public boolean hasVisibleRows(Object fragment) { return !((Fragment) fragment).rows.isEmpty(); }
        @Override public void restore(Object fragment, Object paging) {
            order.add("restore");
            Fragment owner = (Fragment) fragment;
            owner.paging = (Paging) paging;
            owner.rows.removeAll(Arrays.asList("native-empty", "native-progress", "native-end", "native-error"));
        }
        @Override public void request(Object fragment, Object paging) throws ReflectiveOperationException {
            requests.add((Paging) paging);
            order.add("request");
            // Base.loadMore posts the virtual onLoadMore callback onto RecyclerView.
            if (nativeRequestPosted) {
                postedRequests.add(() -> {
                    try { helper.beforeLoadMore(fragment, paging); }
                    catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
                });
            } else helper.beforeLoadMore(fragment, paging);
        }
        @Override public Object showContinue(Object fragment, Runnable action) {
            Control control = new Control(action);
            controls.add(control);
            return control;
        }
        @Override public void dismiss(Object fragment, Object control) { ((Control) control).dismissed = true; }
    }

    @Test public void nativeCallbacksCompleteBeforeBoundedRefillAndRealPagingSurvives() throws Exception {
        Host host = new Host();
        Response first = host.filtered("1", false, "paid", "paid");
        host.complete(first, true);
        assertEquals(Arrays.asList("native completion", "restore", "request"), host.order);
        assertSame(first.paging, host.fragment.paging);
        assertTrue(host.fragment.rows.isEmpty());
        for (int index = 2; index <= 4; index++) host.complete(host.filtered("" + index, false, "paid"), false);
        assertEquals(4, host.completions.size());
        assertEquals(3, host.requests.size());
        assertEquals(1, host.controls.size());
        assertFalse(host.fragment.paging.end);
        assertEquals("4", host.fragment.paging.next);

        host.controls.get(0).action.run();
        assertTrue(host.controls.get(0).dismissed);
        assertSame(host.fragment.paging, host.requests.get(3));
        for (int index = 5; index <= 8; index++) host.complete(host.filtered("" + index, false, "paid"), false);
        assertEquals(7, host.requests.size()); // Explicit click plus a fresh three-request budget.
        assertEquals(2, host.controls.size());
    }

    @Test public void postedNativeRequestsDoNotResetAutomaticBudget() throws Exception {
        Host host = new Host();
        host.nativeRequestPosted = true;
        for (int index = 1; index <= 4; index++) {
            host.complete(host.filtered("" + index, false, "paid"), index == 1);
            if (!host.postedRequests.isEmpty()) host.postedRequests.remove(0).run();
        }
        assertEquals(3, host.requests.size());
        assertEquals(1, host.controls.size());
    }

    @Test public void repeatedAndCyclicCursorsStopAutomaticallyWithoutPretendingEnd() throws Exception {
        Host repeated = new Host();
        repeated.complete(repeated.filtered("1", false, "paid"), true);
        repeated.complete(repeated.filtered("1", false, "paid"), false);
        assertEquals(1, repeated.requests.size());
        assertEquals(1, repeated.controls.size());
        assertFalse(repeated.fragment.paging.end);
        Host cyclic = new Host();
        cyclic.complete(cyclic.filtered("1", false, "paid"), true);
        cyclic.complete(cyclic.filtered("2", false, "paid"), false);
        cyclic.complete(cyclic.filtered("1", false, "paid"), false);
        assertEquals(2, cyclic.requests.size());
        assertEquals(1, cyclic.controls.size());
        assertEquals("1", cyclic.fragment.paging.next);
    }

    @Test public void consumedWrapperMarkersPreserveCumulativeNormalRowsAndOtherSurfaces() throws Exception {
        Host question = new Host();
        question.fragment.rows.add("previous free answer");
        Response reusedWrapper = question.filtered("1", false, "paid");
        question.complete(reusedWrapper, false);
        assertEquals(Collections.singletonList("previous free answer"), question.fragment.rows);
        assertEquals(1, question.requests.size());
        // Same AnswerListWrapper delivers an ordinary response later without being marked.
        reusedWrapper.data = Collections.singletonList("article");
        reusedWrapper.paging = new Paging("2", false);
        question.complete(reusedWrapper, false);
        assertEquals(1, question.requests.size());
        assertEquals(Arrays.asList("previous free answer", "article"), question.fragment.rows);
        for (int index = 3; index <= 6; index++) question.complete(question.filtered("" + index, false, "paid"), false);
        assertEquals(4, question.requests.size());
        assertTrue(question.controls.isEmpty()); // Existing rows retain the ordinary native scroll path.
        Host home = new Host();
        home.complete(home.filtered("1", false, "paid"), true);
        assertEquals(1, home.requests.size()); // Different surface has its own markers and budget.
    }

    @Test public void unmarkedEmptyMixedTerminalAndAbsentCursorKeepNativeDataAndPaging() throws Exception {
        Host ordinary = new Host();
        Response realEmpty = ordinary.filtered("empty", false);
        ordinary.complete(realEmpty, true);
        assertEquals(Collections.singletonList("native-empty"), ordinary.fragment.rows);
        assertNull(ordinary.fragment.paging);
        assertTrue(ordinary.requests.isEmpty());
        Response mixed = ordinary.filtered("mixed", false, "paid", "free", "article", "idea", "video");
        ordinary.complete(mixed, true);
        assertEquals(Arrays.asList("free", "article", "idea", "video"), ordinary.fragment.rows);
        assertTrue(ordinary.requests.isEmpty());
        for (String cursor : Arrays.asList("terminal", "", null)) {
            Host terminal = new Host();
            Response end = terminal.filtered(cursor, true, "paid");
            terminal.complete(end, true);
            assertNull(terminal.fragment.paging); // Preserve the original terminal empty branch.
            assertEquals(Collections.singletonList("native-empty"), terminal.fragment.rows);
            assertTrue(end.paging.end);
            assertEquals(cursor, end.paging.next);
            assertTrue(terminal.requests.isEmpty());
            assertTrue(terminal.controls.isEmpty());
        }
        for (String cursor : Arrays.asList("", null)) {
            Host absent = new Host();
            absent.complete(absent.filtered(cursor, false, "paid"), true);
            assertTrue(absent.requests.isEmpty());
            assertTrue(absent.controls.isEmpty());
            assertFalse(absent.fragment.paging.end);
        }
    }

    @Test public void identityMarkersAndFragmentsCannotBorrowAnotherDeliverysBudget() throws Exception {
        Host host = new Host();
        Response marked = host.filtered("marked", false, "paid");
        Response equalButUnmarked = new Response("ordinary", false);
        assertEquals(marked, equalButUnmarked);
        host.complete(equalButUnmarked, true);
        assertTrue(host.requests.isEmpty());
        host.complete(marked, true);
        Fragment other = new Fragment();
        host.complete(other, host.filtered("other", false, "paid"), true);
        assertEquals(2, host.requests.size());
        assertEquals("other", other.paging.next);
    }

    @Test public void queuedCompletionCapturesRealPagingAndCancelsForNewFlowOrDestroyedView() throws Exception {
        Host host = new Host();
        host.delayed = true;
        Response wrapper = host.filtered("captured", false, "paid");
        Paging actualPaging = wrapper.paging;
        host.complete(wrapper, true);
        assertTrue(host.requests.isEmpty());
        assertNull(host.fragment.paging);
        wrapper.paging = new Paging("later wrapper state", false);
        host.drain();
        assertSame(actualPaging, host.requests.get(0));
        host.complete(host.filtered("cancel refresh", false, "paid"), true);
        host.helper.newFlow(host.fragment);
        host.drain();
        assertEquals(1, host.requests.size());
        host.complete(host.filtered("cancel destroy", false, "paid"), true);
        host.helper.destroy(host.fragment);
        host.fragment.alive = false;
        host.drain();
        assertEquals(1, host.requests.size());
    }


    @Test public void newerDeliveryCancelsQueuedOlderCursorAndItsManualAction() throws Exception {
        for (boolean newerMarked : Arrays.asList(false, true)) {
            Host host = new Host();
            host.delayed = true;
            host.complete(host.filtered("older", false, "paid"), true);
            host.complete(host.filtered("newer", false, newerMarked ? "paid" : "free"), false);
            host.drain();
            assertEquals(newerMarked ? 1 : 0, host.requests.size());
            if (newerMarked) assertEquals("newer", host.requests.get(0).next);
        }
        Host host = new Host();
        host.complete(host.filtered("1", false, "paid"), true);
        host.complete(host.filtered("1", false, "paid"), false);
        Control stale = host.controls.get(0);
        host.delayed = true;
        host.complete(host.filtered("new normal", false, "free"), false);
        stale.action.run(); // New delivery invalidates the action before its posted cleanup runs.
        assertEquals(1, host.requests.size());
        host.drain();
        assertTrue(stale.dismissed);
    }

    @Test public void manualControlDismissesAndCannotRunAfterNonemptyEndOrDestroy() throws Exception {
        for (String reason : Arrays.asList("normal", "end", "destroy", "refresh")) {
            Host host = new Host();
            host.complete(host.filtered("1", false, "paid"), true);
            host.complete(host.filtered("1", false, "paid"), false);
            Control control = host.controls.get(0);
            if (reason.equals("normal")) host.complete(host.filtered("2", false, "free"), false);
            else if (reason.equals("end")) host.complete(host.filtered("2", true, "paid"), false);
            else if (reason.equals("refresh")) host.helper.newFlow(host.fragment);
            else host.helper.destroy(host.fragment);
            assertTrue(control.dismissed);
            control.action.run();
            assertEquals(reason, 1, host.requests.size());
        }
    }
}
