import "dotenv/config";
import nodemailer from "nodemailer";
const { google } = require("googleapis");
let transporter;
const oAuth2Client = new google.auth.OAuth2(
	process.env.CLIENT_ID,
	process.env.CLIENT_SECRET,
	process.env.REDIRECT_URI
  );



if (process.env.NODE_ENV === "development") {
    transporter = nodemailer.createTransport({
        host: "mailhog",
        port: 1025,
    });
} else if (process.env.NODE_ENV === "production") {
    // Configuration for using Gmail with OAuth2
	const accessToken = await oAuth2Client.getAccessToken();
    const transport = nodemailer.createTransport({
      service: "gmail",
      auth: {
        ...CONSTANTS.auth,
        accessToken: accessToken,
      },
    });
}

export default transporter;