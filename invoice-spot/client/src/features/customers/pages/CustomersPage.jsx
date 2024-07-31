import ClearIcon from "@mui/icons-material/Clear";
import DeleteForeverIcon from "@mui/icons-material/DeleteForever";
import GroupIcon from "@mui/icons-material/Group";
import PersonAddAltIcon from "@mui/icons-material/PersonAddAlt";
import {
	Badge,
	Box,
	Button,
	Container,
	CssBaseline,
	Grid,
	Modal,
	Paper,
	Stack,
	styled,
	Table,
	TableBody,
	TableCell,
	TableContainer,
	TableFooter,
	TableHead,
	TablePagination,
	TableRow,
	Typography,
} from "@mui/material";

import * as React from "react";
import { useState } from "react";
import { FaEye, FaUsers } from "react-icons/fa";
import StyledTableCell from "../../../components/StyledTableCell";
import StyledTableRow from "../../../components/StyledTableRow";

import { useNavigate } from "react-router-dom";
import { toast } from "react-toastify";
import Spinner from "../../../components/Spinner";
import StyledDivider from "../../../components/StyledDivider";
import TablePaginationActions from "../../../components/TablePaginationActions";
import "../../../styles/customer-button.css";

import {
	useDeleteCustomerMutation,
	useGetAllUserCustomersQuery,
} from "../customersApiSlice";
import CustomerSVG from "./CustomerSVG";

const modalStyle = {
	position: "absolute",
	top: "50%",
	left: "50%",
	transform: "translate(-50%, -50%)",
	width: 400,
	bgcolor: "background.paper",
	border: "2px solid #000",
	borderRadius: "25px",
	boxShadow: 24,
	p: 4,
};

const StyledButton = styled(Button)({
	boxShadow: "0 0 0 0 #f0f0f0, 0 0 0 0 rgba(227, 115, 14, 1)",
});

const CustomersPage = () => {
	const navigate = useNavigate();
	const [page, setPage] = useState(0);
	const [rowsPerPage, setRowsPerPage] = useState(5);

	const { data, isLoading } = useGetAllUserCustomersQuery(page);

	const [deleteCustomer] = useDeleteCustomerMutation();

	const rows = data?.myCustomers;

	
	const [open, setOpen] = useState(false);
	
	const [selectedCustomer, setSelectedCustomer] = useState("");
	const handleOpen = (customer) => {
		setSelectedCustomer(customer);
		setOpen(true);
	};

	const handleClose = () => setOpen(false);

	const emptyRows =
		page > 0 ? Math.max(0, (1 + page) * rowsPerPage - rows?.length) : 0;

	const handleChangePage = (event, newPage) => {
		setPage(newPage);
	};

	const handleChangeRowsPerPage = (event) => {
		setRowsPerPage(parseInt(event.target.value, 10));
		setPage(0);
	};

	const deleteCustomerHandler = async (id) => {
		try {
			await deleteCustomer(id).unwrap();
			toast.success("Customer deleted successfully");
		} catch (err) {
			const message = err.data.message;
			toast.error(message);
		}
	};

	return (
		<Container component="main"  sx={{ mt: 10, maxWidth:{xs:"25rem", sm:"lg"} }}>
			<CssBaseline />
			<Stack direction="row" justifyContent="center" alignItems="center">
				<FaUsers className="auth-svg" />
				<Typography variant="h1" sx={{
            fontSize: {
                xs: '1rem', 
                sm: '3rem',
            },
        }}>Customers</Typography>
			</Stack>
			<StyledDivider />

			<Grid>
				<Grid item>
					<Stack direction={{ xs: 'column', sm: 'row' }}  sx={{alignItems:{xs:"center", sm:"center"},justifyContent:{xs:"center",sm:"space-between"}, mt:{xs:"10%", sm:"0%"}}}>
						<Stack direction={{ xs: 'column', sm: 'row' }}>
							<Typography variant="h4"  sx={{
            fontSize: {
                xs: '1.25rem',
                sm: '2rem',
				
            }}}>Total: </Typography>
							<Badge
								badgeContent={data?.totalCustomers || "0"}
								color="primary"
								sx={{
									marginTop: {xs:"30%", sm:"3px"},
									marginLeft: "5px",
									marginBottom: {xs:"30%",sm:"5px"},
								}}
							>
								<GroupIcon color="action" fontSize="large" />
							</Badge>
						</Stack>

						<StyledButton
							className="new-customer-btn"
							variant="contained"
							color="primary"
							startIcon={<PersonAddAltIcon />}
							onClick={() => navigate("/create-customer")}
							sx={{mb:{xs:"20%", sm:"0%"}
						}}
						>
							Create New Customer
						</StyledButton>
					</Stack>
				</Grid>
			</Grid>

			{isLoading ? (
				<Spinner />
			) : !data?.myCustomers.length ? (
				<CustomerSVG />
			) : (
				<TableContainer
					component={Paper}
					sx={{ marginBottom: "100px", marginTop: "15px" }}
				>
					{isLoading && <Spinner />}
					<Table sx={{ minWidth: 650 }} aria-label="simple table">
						<TableHead>
							<TableRow>
								<StyledTableCell>No</StyledTableCell>
								<StyledTableCell>Name</StyledTableCell>
								<StyledTableCell>Email</StyledTableCell>
								<StyledTableCell>Phone</StyledTableCell>
								<StyledTableCell>Account No</StyledTableCell>
								<StyledTableCell>View Details</StyledTableCell>
								<StyledTableCell>Delete</StyledTableCell>
							</TableRow>
						</TableHead>

						<TableBody>
							{(rowsPerPage > 0
								? rows.slice(
										page * rowsPerPage,
										page * rowsPerPage + rowsPerPage
								  )
								: rows
							).map((row, index) => (
								<StyledTableRow
									key={row._id}
									sx={{
										"&:last-child td, &:last-child th": {
											border: 0,
										},
										cursor: "pointer",
									}}
								>
									<StyledTableCell component="th" scope="row">
										{index + 1}
									</StyledTableCell>
									<StyledTableCell>
										{row.name}
									</StyledTableCell>
									<StyledTableCell>
										{row.email}
									</StyledTableCell>
									<StyledTableCell>
										{row.phoneNumber}
									</StyledTableCell>

									<StyledTableCell>
										{row.accountNo}
									</StyledTableCell>

									<StyledTableCell>
										<Box
											sx={{
												"&:hover": {
													cursor: "pointer",
												},
											}}
										>
											<FaEye
												color="error"
												fontSize="large"
												sx={{ cursor: "pointer" }}
												onClick={() =>
													navigate(
														`/single-customer/${row._id}`
													)
												}
											/>
										</Box>
									</StyledTableCell>

									<StyledTableCell align="">
										<Box
											sx={{
												"&:hover": {
													cursor: "pointer",
												},
											}}
										>
											<ClearIcon
												color="error"
												fontSize="large"
												sx={{ cursor: "pointer" }}
												onClick={() => handleOpen(row)}
											/>
										</Box>
									</StyledTableCell>
								</StyledTableRow>
							))}

							{open && (
								<Modal
									open={open}
									onClose={handleClose}
									aria-labelledby="modal-modal-title"
									aria-describedby="modal-modal-description"
								>
									<Box sx={modalStyle}>
										<Typography
											id="modal-modal-title"
											variant="h6"
											component="h2"
										>
											{`Are you sure you want to
                         delete this customer,${selectedCustomer.name}?`}
										</Typography>
										<Button
											id="modal-modal-description"
											sx={{ mt: 2 }}
											color="darkRed"
											size="large"
											fullWidth
											variant="contained"
											endIcon={
												<DeleteForeverIcon
													sx={{
														color: "white",
													}}
												/>
											}
											onClick={() => {
												deleteCustomerHandler(
													`${selectedCustomer._id}`
												);
												handleClose();
											}}
										>
											<Typography
												variant="h5"
												sx={{ color: "white" }}
											>
												Delete Customer
											</Typography>
										</Button>
									</Box>
								</Modal>
							)}
							{emptyRows > 0 && (
								<TableRow style={{ height: 53 * emptyRows }}>
									<TableCell colSpan={6} />
								</TableRow>
							)}
						</TableBody>

						{/* Footer with the pagination */}
						<TableFooter>
							<TableRow>
								<TablePagination
									rowsPerPageOptions={[
										5,
										10,
										25,
										{ label: "All", value: -1 },
									]}
									colSpan={9}
									count={rows.length}
									rowsPerPage={rowsPerPage}
									page={page}
									SelectProps={{
										inputProps: {
											"aria-label": "rows per page",
										},
										native: true,
									}}
									onPageChange={handleChangePage}
									onRowsPerPageChange={
										handleChangeRowsPerPage
									}
									ActionsComponent={TablePaginationActions}
								/>
							</TableRow>
						</TableFooter>
					</Table>
				</TableContainer>
			)}
		</Container>
	);
};

export default CustomersPage;
