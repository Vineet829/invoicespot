import { Component } from "react";
import { primaryButton, secondaryButton } from "./workspace/pastel";

// Whatever goes wrong while a page draws, the visitor gets a way back instead
// of a blank screen: reload, or sign in again with a fresh session. It needs
// nothing else of the app, which may be what failed.
class ErrorBoundary extends Component {
  state = { failed: false };

  static getDerivedStateFromError() {
    return { failed: true };
  }

  componentDidCatch(error, info) {
    console.error("The page failed to draw", error, info.componentStack);
  }

  render() {
    if (!this.state.failed) {
      return this.props.children;
    }
    return (
      <main className="flex min-h-screen items-center justify-center px-4 py-10">
        <div className="w-full max-w-[460px] rounded-[28px] border border-white/90 bg-white/70 p-8 shadow-[0_24px_60px_-28px_rgba(48,35,79,0.35)] max-[700px]:p-6">
          <h1 className="font-display text-[34px] font-extralight leading-[1.1] tracking-[-0.045em] text-spot-ink max-[700px]:text-[30px]">
            Something went wrong<span className="text-spot-brand">.</span>
          </h1>
          <p className="mt-3 text-[14.5px] leading-[1.6] text-spot-sub">
            This page hit a problem it couldn’t recover from. Reloading usually
            fixes it. If it keeps happening, sign in again.
          </p>
          <div className="mt-7 flex flex-wrap gap-2.5 [&>*]:flex-1">
            <button
              type="button"
              className={primaryButton}
              onClick={() => window.location.reload()}
            >
              Reload the page
            </button>
            <button
              type="button"
              className={secondaryButton}
              onClick={() => {
                localStorage.removeItem("user");
                window.location.assign("/login");
              }}
            >
              Sign in again
            </button>
          </div>
        </div>
      </main>
    );
  }
}

export default ErrorBoundary;
