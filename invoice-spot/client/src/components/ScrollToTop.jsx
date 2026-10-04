import { useEffect, useRef } from "react";
import { useLocation, useNavigationType } from "react-router-dom";

// A link to another page opens it at its top. The router leaves the window
// where it was, so a link far down one page would open the next one as far
// down. Back and forward keep the place the browser remembers, a link to a
// section (such as #ageing) leaves the scrolling to its page, and a new
// address for the same page, such as another period, stays where it is.
const ScrollToTop = () => {
  const { pathname, hash } = useLocation();
  const navigation = useNavigationType();
  const page = useRef(pathname);
  useEffect(() => {
    if (page.current === pathname) return;
    page.current = pathname;
    if (navigation !== "POP" && !hash) window.scrollTo(0, 0);
  }, [pathname, hash, navigation]);
  return null;
};

export default ScrollToTop;
