import {
	Box,
	Container,
	Divider,
	Grid,
	Link,
	Typography,
  } from "@mui/material";
  import { FaSignInAlt } from "react-icons/fa";
  import { Link as RouterLink } from "react-router-dom";
  import StyledDivider from "../../../components/StyledDivider";
  import AuthWrapper from "../forms/AuthWrapper";
  import LoginForm from "../forms/LoginForm";
  
  const LoginPage = () => {
	return (
	  <AuthWrapper>
		<Container
		  component="main"
		  
		  sx={{
			border: "2px solid #e4e5e7",
			borderRadius: "25px",
			py: 2,
			px: { xs: 1, sm: 2 },
			maxWidth:{xs:"25rem", sm:"40rem"}
		  }}
		>
		  <Grid container spacing={2}> {/* Ensure that container prop is added for proper spacing */}
			<Grid item xs={12}>
			  <Box
				sx={{
				  display: "flex",
				  flexDirection: "row",
				  justifyContent: "center",
				  alignItems: "center",
				}}
			  >
				<FaSignInAlt className="auth-svg" />
				<Typography variant="h5" sx={{ fontSize: { xs: '1.5rem', sm: '2rem' } }}>Log In</Typography> {/* Adjust font size */}
			  </Box>
			  <StyledDivider />
			</Grid>
			{/* login form */}
			<LoginForm />
			<Divider
			  sx={{ flexGrow: 1, mb: 1, mt: 1 ,}}
			  orientation="horizontal"
			/>
			{/* forgot password */}
			<Grid item xs={12}>
			  <Box
				sx={{
				  justifyContent: "center",
				  display: "flex",
				  alignItems: "center",
				}}
			  >
				<Typography variant="body1" sx={{ textAlign: 'center', fontSize: { xs: '0.875rem', sm: '1rem' } }}> {/* Adjust font size for smaller screens */}
				  Don't have an account?
				  <Link
					variant="body1"
					component={RouterLink}
					to="/register"
					sx={{ textDecoration: "none", ml: 0.5 }} // Adjust margin
				  >
					Sign Up Here
				  </Link>
				</Typography>
			  </Box>
			</Grid>
			<Divider
			  sx={{ flexGrow: 1, mb: 1, mt: 1 }}
			  orientation="horizontal"
			/>
			{/* resend email verification button */}
			<Grid item xs={12}>
			  <Box
				sx={{
				  justifyContent: "center",
				  display: "flex",
				  alignItems: "center",
				}}
			  >
				<Typography variant="body1" sx={{ textAlign: 'center', fontSize: { xs: '0.875rem', sm: '1rem' } }}> {/* Adjust font size for smaller screens */}
				  Didn't get the verification email?
				  <Link
					variant="body1"
					component={RouterLink}
					to="/resend"
					sx={{ textDecoration: "none", ml: 0.5 }} // Adjust margin
				  >
					Resend Email
				  </Link>
				</Typography>
			  </Box>
			</Grid>
		  </Grid>
		</Container>
	  </AuthWrapper>
	);
  };
  
  export default LoginPage;