import Box from "@mui/material/Box";
import { FcGoogle } from "react-icons/fc";

const GoogleLogin = () => {
	const google = () => {
		// TODO: change this in production
		window.open("www.vineetpersonal.site/api/v1/auth/google", "_self");
	};
	return (
		<Box sx={{ cursor: "pointer" }} onClick={google}>
			<FcGoogle className="google-icon" />
		</Box>
	);
};

export default GoogleLogin;