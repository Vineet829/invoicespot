import { Container, Grid, Stack, Typography } from "@mui/material";
import DocSVG from "../../../../images/add_bill.svg";

const DocumentSVG = () => {
	return (
		<Container component="main" maxWidth="lg">
			<Grid>
				<Grid item>
					<Stack
						alignItems="center"
						justifyContent="center"
						sx={{ mt: 2, rowGap:{xs:"50px", sm:"0px"}, ml:{xs:7, sm:0} }}
					>
						<Typography
							variant="h5"
							sx={{
								marginBottom: "10px",
								fontSize: { xs: '1rem', sm: '1.5rem' }, // Adjust font size for mobile
							}}
						>
							Sadly, You have no Documents yet. To create one click
							👉 👉 👉
						</Typography>
						<img
							src={DocSVG}
							alt="customer logo"
							className="customer-svg"
							style={{
								maxWidth: '100%', // Ensure the image is responsive and does not overflow its container
								height: 'auto',
								width: { xs: '150px', sm: 'auto' }, // Make the image smaller on mobile
							}}
						/>
					</Stack>
				</Grid>
			</Grid>
		</Container>
	);
};

export default DocumentSVG;
