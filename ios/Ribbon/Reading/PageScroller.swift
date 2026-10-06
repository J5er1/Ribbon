import SwiftUI
import UIKit

// The page's moves inside a chapter, made on the scroll view itself (I40).
//
// The reading page is a lazy stack of chapters, and SwiftUI's scroll proxy
// can be trusted to reach only the stack's own rows — a chapter's head. The
// moves inside a chapter — a landing's second half, onto its verse; every
// step of a follow; the rubber band's return — aimed instead at a 1-point
// mark set inside the chapter's row, and on the phones themselves the proxy
// never found it: the move "finished", nothing moved, and the page stayed
// at the chapter's head. That was the book opening at the head of the
// chapter rather than on your verse, and a follow that landed on chapter
// heads and then never stepped, through every fix to the guess above it.
//
// So those moves are made here: on the UIKit scroll view that backs the
// page, by the distance the page has measured from the chapter's own frame.
// Where they go no longer depends on the scroll view finding a view by its
// id, and a move of a measured distance is exact. They ease on a display
// link, a frame at a time, so the lazy stack builds what comes into view as
// it comes, and on the same ease-out every other move uses.

/// Moves the page's scroll view by a measured distance.
@MainActor
final class PageScroller {
    /// The scroll view that backs the page, found by `PageScrollerProbe`.
    fileprivate weak var scrollView: UIScrollView?
    /// The probe, asked to look again if a move comes before it has found
    /// the scroll view.
    fileprivate weak var probe: PageScrollerProbe.Probe?
    private var link: CADisplayLink?
    private var run: Run?

    private struct Run {
        var from: CGFloat
        var to: CGFloat
        var start: CFTimeInterval
        var duration: CFTimeInterval
        var done: () -> Void
    }

    /// Moves the page `distance` points on through the book — back when
    /// negative — over `duration` seconds on the ease-out every move uses,
    /// or at once when it is zero. Held to the page's own ends. `done` runs
    /// once it is there — on the next turn of the main queue, so the page
    /// has measured where the move left it — or then too when there is
    /// nothing to move: a move that cannot be made is still finished, as the
    /// scroll proxy's were.
    func move(by distance: CGFloat, duration: Double, done: @escaping () -> Void) {
        finish()
        if scrollView == nil { probe?.find() }
        guard let scrollView, distance.isFinite else {
            Self.later(done)
            return
        }
        // A fling still running would carry the page on past where this
        // puts it. Setting the offset where it is ends it.
        if scrollView.isDecelerating {
            scrollView.setContentOffset(scrollView.contentOffset, animated: false)
        }
        let inset = scrollView.adjustedContentInset
        let lowest = -inset.top
        let highest = max(lowest, scrollView.contentSize.height - scrollView.bounds.height + inset.bottom)
        let from = scrollView.contentOffset.y
        let to = min(max(from + distance, lowest), highest)
        guard duration > 0, abs(to - from) >= 0.5 else {
            scrollView.setContentOffset(CGPoint(x: scrollView.contentOffset.x, y: to), animated: false)
            Self.later(done)
            return
        }
        run = Run(from: from, to: to, start: CACurrentMediaTime(), duration: duration, done: done)
        let link = CADisplayLink(target: PageScrollerTicker(self), selector: #selector(PageScrollerTicker.tick))
        link.add(to: .main, forMode: .common)
        self.link = link
    }

    /// One frame of a move.
    fileprivate func tick() {
        guard let run, let scrollView else {
            finish()
            return
        }
        // A finger on the page takes it: the move stops where it is.
        if scrollView.isTracking {
            finish()
            return
        }
        let t = min(1, max(0, (CACurrentMediaTime() - run.start) / run.duration))
        let y = run.from + (run.to - run.from) * CGFloat(Self.easeOut(t))
        scrollView.setContentOffset(CGPoint(x: scrollView.contentOffset.x, y: y), animated: false)
        if t >= 1 { finish() }
    }

    /// Ends the move under way, if there is one, and says it is done.
    private func finish() {
        link?.invalidate()
        link = nil
        guard let ended = run else { return }
        run = nil
        Self.later(ended.done)
    }

    private static func later(_ done: @escaping () -> Void) {
        DispatchQueue.main.async { done() }
    }

    /// SwiftUI's `easeOut` — the cubic Bézier (0, 0, 0.58, 1) — at `t`.
    nonisolated static func easeOut(_ t: Double) -> Double {
        guard t > 0 else { return 0 }
        guard t < 1 else { return 1 }
        // x(s) = 3(1-s)s²·0.58 + s³, y(s) = 3(1-s)s² + s³, both rising.
        func x(_ s: Double) -> Double { 3 * (1 - s) * s * s * 0.58 + s * s * s }
        var low = 0.0, high = 1.0, s = t
        for _ in 0..<24 {
            s = (low + high) / 2
            if x(s) < t { low = s } else { high = s }
        }
        return 3 * (1 - s) * s * s + s * s * s
    }
}

/// The display link's target: it holds the scroller weakly, so a page that
/// has gone does not stay alive for a move it no longer needs.
@MainActor
private final class PageScrollerTicker: NSObject {
    weak var scroller: PageScroller?

    init(_ scroller: PageScroller) {
        self.scroller = scroller
    }

    @objc func tick() {
        scroller?.tick()
    }
}

/// Finds the scroll view that backs the page, from inside it: set behind the
/// page's stack, its view's nearest scrolling ancestor is the page's own.
struct PageScrollerProbe: UIViewRepresentable {
    let scroller: PageScroller

    func makeUIView(context: Context) -> Probe {
        let view = Probe()
        view.scroller = scroller
        scroller.probe = view
        view.isUserInteractionEnabled = false
        view.isAccessibilityElement = false
        return view
    }

    func updateUIView(_ view: Probe, context: Context) {
        view.scroller = scroller
        scroller.probe = view
        view.find()
    }

    final class Probe: UIView {
        weak var scroller: PageScroller?

        override func didMoveToWindow() {
            super.didMoveToWindow()
            find()
        }

        func find() {
            var next = superview
            while let view = next {
                // A chapter's text is a UITextView, which scrolls too; the
                // probe is never inside one, but the page's own is what
                // is wanted.
                if let scrollView = view as? UIScrollView, !(view is UITextView) {
                    scroller?.scrollView = scrollView
                    return
                }
                next = view.superview
            }
        }
    }
}
