import asyncHandler from "express-async-handler";
import User from "../../models/userModel.js";

// $-title   Delete User Account
// $-path    DELETE /api/v1/user/:id
// $-auth    Private/Admin

const deleteUserAccount = asyncHandler(async (req, res) => {
	try {
		const result = await User.findByIdAndDelete(req.params.id);

		if (result) {
			res.json({
				success: true,
				message: `User ${result.firstName} deleted successfully`,
			});
		} else {
			res.status(404).json({ message: "User not found" });
		}
	} catch (error) {
		console.error(error);
		res.status(500).json({ message: "Error deleting user" });
	}
});

export default deleteUserAccount;
