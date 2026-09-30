import { useSelector } from "react-redux";
import { Link } from "react-router-dom";
import useTitle from "../hooks/useTitle";
import PublicPage from "./public/PublicPage";
import { primaryButton, secondaryButton } from "./workspace/pastel";

const NotFound = () => {
  useTitle("Page not found - Invoice Spot");
  const user = useSelector((state) => state.auth.user);
  return (
    <PublicPage>
      <p
        className="font-display text-[80px] font-extralight leading-none tracking-[-0.06em] text-spot-brand max-[700px]:text-[64px]"
        aria-hidden="true"
      >
        404
      </p>
      <h1 className="mt-5 font-display text-[34px] font-extralight leading-[1.1] tracking-[-0.045em] text-spot-ink max-[700px]:text-[30px] [&>span]:text-spot-brand">
        Page not found<span>.</span>
      </h1>
      <p className="mt-3 text-[14.5px] leading-[1.6] text-spot-sub">
        The page you’re looking for doesn’t exist or may have moved.
      </p>
      <div className="mt-7 flex flex-wrap gap-2.5 [&>*]:flex-1">
        <Link to="/" className={primaryButton}>
          Back to homepage
        </Link>
        {user && (
          <Link to="/dashboard" className={secondaryButton}>
            Open dashboard
          </Link>
        )}
      </div>
    </PublicPage>
  );
};

export default NotFound;
