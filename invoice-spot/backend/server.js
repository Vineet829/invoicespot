import dotenv from 'dotenv';
import chalk from "chalk";
import path from "path";
import cookieParser from "cookie-parser";
import express from "express";
import morgan from "morgan";
import connectionToDB from "./config/connectDB.js";
import { morganMiddleware, systemLogs } from "./utils/Logger.js";
import mongoSanitize from "express-mongo-sanitize";
import { errorHandler, notFound } from "./middleware/errorMiddleware.js";
import authRoutes from "./routes/authRoutes.js";
import userRoutes from "./routes/userRoutes.js";
import { apiLimiter } from "./middleware/apiLimiter.js";
import passport from "passport";
// import googleAuth from "./config/passportSetup.js";
import customerRoutes from "./routes/customerRoutes.js";
import documentRoutes from "./routes/documentRoutes.js";
import uploadRoutes from "./routes/uploadRoutes.js";
const __dirname = path.resolve();
console.log(path.join(__dirname, "client/build"))
try {
    const result = dotenv.config({ path: path.resolve(__dirname, './.env') });

    if (result.error) {
        throw new Error(`Failed to load .env file: ${result.error}`);
    }

    // Log the loaded environment variables for debugging
    console.log('MONGO_URI:', process.env.MONGO_URI);
    console.log('DB_NAME:', process.env.DB_NAME);
    console.log('PORT:', process.env.PORT);
} catch (error) {
    console.error(`Error loading .env file: ${error.message}`);
    process.exit(1); // Exit the process if the .env file cannot be loaded
}
await connectionToDB();

const app = express();


app.use("/uploads", express.static(path.join(__dirname, "/uploads")));
app.use("/docs", express.static(path.join(__dirname, "/docs")));

if (process.env.NODE_ENV === "development") {
	app.use(morgan("dev"));
}

app.use(express.json());

app.use(express.urlencoded({ extended: false }));

app.use(passport.initialize());
// googleAuth();

app.use(cookieParser());

app.use(mongoSanitize());

app.use(morganMiddleware);

app.get("/api/v1/test", (req, res) => {
	res.json({ Hi: "Welcome to the Invoice App" });
});

app.use("/api/v1/auth", authRoutes);
app.use("/api/v1/user", apiLimiter, userRoutes);
app.use("/api/v1/customer", apiLimiter, customerRoutes);
app.use("/api/v1/document", apiLimiter, documentRoutes);
app.use("/api/v1/upload", apiLimiter, uploadRoutes);

// serve frontend
if (process.env.NODE_ENV === "production") {
	app.use(express.static(path.join(__dirname, "client/build")));

	app.get("*", (req, res) =>
		res.sendFile(path.resolve(__dirname, "client", "build", "index.html"))
	);
} else {
	app.get("/", (req, res) => res.send("Please set to production"));
}

app.use(notFound);
app.use(errorHandler);

const PORT = process.env.PORT || 1997;

app.listen(PORT, () => {
	console.log(
		`${chalk.green.bold("✔")} 👍 Server running in ${chalk.yellow.bold(
			process.env.NODE_ENV
		)} mode on port ${chalk.blue.bold(PORT)}`
	);
	systemLogs.info(
		`Server running in ${process.env.NODE_ENV} mode on port ${PORT}`
	);
});