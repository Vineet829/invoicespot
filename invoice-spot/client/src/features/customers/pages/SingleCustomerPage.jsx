import AttachEmailIcon from "@mui/icons-material/AttachEmail";
import BadgeIcon from "@mui/icons-material/Badge";
import CottageTwoToneIcon from "@mui/icons-material/CottageTwoTone";
import EditTwoToneIcon from "@mui/icons-material/EditTwoTone";
import NumbersTwoToneIcon from "@mui/icons-material/NumbersTwoTone";
import PermPhoneMsgTwoToneIcon from "@mui/icons-material/PermPhoneMsgTwoTone";
import PushPinTwoToneIcon from "@mui/icons-material/PushPinTwoTone";
import RequestQuoteTwoToneIcon from "@mui/icons-material/RequestQuoteTwoTone";
import VpnLockTwoToneIcon from "@mui/icons-material/VpnLockTwoTone";
import {
	Box,
	Container,
	Button,
	List,
	CssBaseline,
	ListItem,
	ListItemIcon,
	ListItemText,
	Stack,
	Typography,
} from "@mui/material";

import { GrUser } from "react-icons/gr";
import { useNavigate, useParams } from "react-router-dom";
import Spinner from "../../../components/Spinner";
import StyledDivider from "../../../components/StyledDivider";
import { useGetSingleCustomerQuery } from "../customersApiSlice";

function capitalizeFirstLetter(string) {
	return string.charAt(0).toUpperCase() + string.slice(1);
}

const SingleCustomerPage = () => {
	const { custId } = useParams();

	const navigate = useNavigate();

	const goBack = () => navigate(-1);

	const { data, isLoading } = useGetSingleCustomerQuery(custId);

	return (
		<Container
			component="main"
			
			sx={{
				border: "2px solid  #e4e5e7",
				borderRadius: "25px",
				py: 2,
				mt: 10,
			    maxWidth:{xs:"25rem", sm:"md"}
			}}
		>
			<CssBaseline />
			<Box
				sx={{
					display: "flex",
					flexDirection: "row",
					justifyContent: "center",
					alignItems: "center",
				}}
			>
				<GrUser fontSize="40px" />
				<Typography variant="h3" sx={{
            fontSize: {
                xs: '1.25rem', 
                sm: '2rem', 
            },
        }}>
					{data?.customer.name.split(" ")[0]}'s Info
				</Typography>

				<Button
					variant="contained"
					color="warning"
					size="small"
					sx={{ fontSize: "1rem", ml: "10px" }}
					onClick={goBack}
				>
					Go Back
				</Button>
			</Box>
			<StyledDivider />
			{isLoading ? (
				<Spinner />
			) : (
				<Box
					sx={{
						display: "flex",
						flexDirection: "column",
						alignItems: "center",
					}}
				>
					<Box
						sx={{
							display: "flex",
							flexDirection: {xs:"column",sm:"row"},
							ml:{xs:"10%", sm:"0%"}
						}}
					>
						<List sx={{ width: "50%" }}>
							
							<ListItem>
								<ListItemIcon>
									<BadgeIcon fontSize="large" />
								</ListItemIcon>
								<ListItemText
									primary={`${capitalizeFirstLetter(
										data?.customer.name
									)}`}
								/>
							</ListItem>
							
							<ListItem>
								<ListItemIcon>
									<AttachEmailIcon fontSize="large" />
								</ListItemIcon>
								<ListItemText primary={data?.customer.email} />
							</ListItem>
							
							<ListItem>
								<ListItemIcon>
									<NumbersTwoToneIcon fontSize="large" />
								</ListItemIcon>
								<ListItemText
									primary={`No : ${data?.customer.accountNo}`}
								/>
							</ListItem>
							
							<ListItem>
								<ListItemIcon>
									<RequestQuoteTwoToneIcon fontSize="large" />
								</ListItemIcon>
								<ListItemText
									primary={
										data?.customer.vatTinNo
											? `VAT/TIN : ${data?.customer.vatTinNo}`
											: "VAT/TIN : ...................."
									}
								/>
							</ListItem>
						</List>

						
						<List sx={{ width: "50%" }}>
							
							<ListItem>
								<ListItemIcon>
									<CottageTwoToneIcon fontSize="large" />
								</ListItemIcon>
								<ListItemText
									primary={
										data?.customer.address
											? `Address : ${data?.customer?.address}`
											: "Address : ...................."
									}
								/>
							</ListItem>

							
							<ListItem>
								<ListItemIcon>
									<PushPinTwoToneIcon fontSize="large" />
								</ListItemIcon>
								<ListItemText
									primary={
										data?.customer.city
											? `City : ${data?.customer.city}`
											: "City : ...................."
									}
								/>
							</ListItem>

							
							<ListItem>
								<ListItemIcon>
									<VpnLockTwoToneIcon fontSize="large" />
								</ListItemIcon>
								<ListItemText
									primary={
										data?.customer.country
											? `Country : ${data?.customer.country}`
											: "Country : ...................."
									}
								/>
							</ListItem>

							
							<ListItem>
								<ListItemIcon>
									<PermPhoneMsgTwoToneIcon fontSize="large" />
								</ListItemIcon>
								<ListItemText
									primary={
										data?.customer.phoneNumber
											? `Phone : ${data?.customer?.phoneNumber}`
											: "Phone : ...................."
									}
								/>
							</ListItem>
						</List>
					</Box>

					<Stack direction="row" justifyContent="center">
						<Button
							sx={{ mt: 3, mb: {xs:"30%",sm:2}, }}
							fullWidth
							variant="contained"
							color="primary"
							size="large"
							endIcon={<EditTwoToneIcon />}
							onClick={() =>
								navigate(`/edit-customer/${data?.customer._id}`)
							}
						>
							<Typography variant="h5" sx={{ fontSize: {
                xs: '1rem', 
                sm: '1.3rem', 
            }}}>
								Edit Customer Info
							</Typography>
						</Button>
					</Stack>
				</Box>
			)}
		</Container>
	);
};

export default SingleCustomerPage;
