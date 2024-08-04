import express from "express";
import createDocument from "../controllers/documents/createDocument.js";
import deleteDocument from "../controllers/documents/deleteDocument.js";
import getAllUserDocuments from "../controllers/documents/getAllUserDocuments.js";
import getSingleUserDocument from "../controllers/documents/getSingleUserDocument.js";
import updateDocument from "../controllers/documents/updateDocument.js";
import createDocumentPayment from "../controllers/documents/createPayment.js";
import {
	generatePDF,
	getPDF,
	sendDocument,
} from "../controllers/documents/generatePDF.js";


import checkAuth from "../middleware/checkAuthMiddleware.js";

const router = express.Router();

router.route("/create").post(checkAuth, createDocument);

router.route("/all").get(checkAuth, getAllUserDocuments);

router.route("/:id/payment").post(checkAuth, createDocumentPayment);

router
	.route("/:id")
	.patch(checkAuth, updateDocument)
	.get(checkAuth, getSingleUserDocument)
	.delete(checkAuth, deleteDocument);

router.route("/generate-pdf").post(generatePDF);
router.route("/get-pdf").get(getPDF);
router.route("/send-pdf").post(sendDocument);

export default router;
