import express from "express";

import createCustomer from "../controllers/customers/createCustomer.js";
import deleteCustomer from "../controllers/customers/deleteCustomer.js";
import getAllUserCustomers from "../controllers/customers/getAllUserCustomers.js";
import getSingleUserCustomer from "../controllers/customers/getSingleUserCustomer.js";
import updateCustomerInfo from "../controllers/customers/updateCustomerInfo.js";
import checkAuth from "../middleware/checkAuthMiddleware.js";

const router = express.Router();

router.route("/create").post(checkAuth, createCustomer);

router.route("/all").get(checkAuth, getAllUserCustomers);

router
	.route("/:id")
	.get(checkAuth, getSingleUserCustomer)
	.patch(checkAuth, updateCustomerInfo)
	.delete(checkAuth, deleteCustomer);

export default router;
