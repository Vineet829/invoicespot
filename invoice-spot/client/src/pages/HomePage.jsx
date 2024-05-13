import { Box, Button, Grid, Link, styled, Typography, useTheme, useMediaQuery } from "@mui/material";
import { Link as RouterLink, useNavigate } from "react-router-dom";
import "../styles/homepage.css";

const StyledTypography = styled(Typography)(({ theme }) => ({
    fontSize: "12rem",
    [theme.breakpoints.down("sm")]: {
        fontSize: "6rem", // Adjusted for smaller screens
    },
}));

const CreateAccountButton = styled(Button)(({ theme }) => ({
    borderColor: "#000000",
    borderRadius: "25px",
    border: "3px solid",
    fontSize: "1rem", // Base font size
    [theme.breakpoints.up("sm")]: {
        fontSize: "1.5em", // Larger screens
    },
    "&:hover": {
        borderColor: "#07f011",
        boxShadow: "none",
        border: "2px solid",
    },
}));

const HomePage = () => {
    const navigate = useNavigate();
    const theme = useTheme();
    const isMobile = useMediaQuery(theme.breakpoints.down("sm"));

    return (
        <>
            <header className="masthead main-bg-image">
                <Grid container justifyContent="center">
                    <Grid item xs={12} sm={10} md={8} lg={6}>
                        <Box sx={{ display: "flex", flexDirection: "column", alignItems: "center", p: isMobile ? 2 : 5 }}>
                            <StyledTypography
                                variant="h1"
                                align="center"
                                sx={{ textTransform: "uppercase", mt: isMobile ? 8 : 16 }}
                                className="homepage-header"
                            >
                                Invoice Spot
                            </StyledTypography>
                            <Typography
                                align="center"
                                variant="h4"
                                component="div"
                                gutterBottom
                                sx={{ color: "rgba(255,255,255,0.6)", fontSize: isMobile ? "1.25rem" : "inherit", mt:{xs:"20%", sm:"0%"}, mb:{xs:"20%", sm:"0%"} }}
                            >
                                Whatever business you run, Creating Invoices, Receipts and Quotations is made easy with our app.
                            </Typography>
                            <Box
                                sx={{
                                    display: "flex",
                                    flexDirection: "row",
                                    justifyContent: "center",
                                    mt: 5,
                                }}
                            >
                                <CreateAccountButton
                                    variant="contained"
                                    color="success"
                                    size="large"
                                    onClick={() => navigate("/register")}
                                >
                                    <Link
                                        component={RouterLink}
                                        to="/register"
                                        sx={{
                                            textDecoration: "none",
                                            color: "white",
                                            fontSize: isMobile ? "1rem" : "2rem",
                                        }}
                                    >
                                        Create Account
                                    </Link>
                                </CreateAccountButton>
                            </Box>
                        </Box>
                    </Grid>
                </Grid>
            </header>
        </>
    );
};

export default HomePage;

