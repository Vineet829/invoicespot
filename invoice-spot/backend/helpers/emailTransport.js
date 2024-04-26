import "dotenv/config";
import nodemailer from "nodemailer";
import { google } from "googleapis";
let transporter
const oAuth2Client = new google.auth.OAuth2(
	process.env.GOOGLE_CLIENT_ID,
	process.env.GOOGLE_CLIENT_SECRET,
	process.env.GOOGLE_CALLBACK_URL
  );


  oAuth2Client.setCredentials({ refresh_token: process.env.REFRESH_TOKEN });

if (process.env.NODE_ENV === "development") {
    transporter = nodemailer.createTransport({
        host: "mailhog",
        port: 1025,
    });
} else if (process.env.NODE_ENV === "production") {
    // Configuration for using Gmail with OAuth2
	const accessToken = await oAuth2Client.getAccessToken();
    transporter = nodemailer.createTransport({
      service: "gmail",
      auth: {
        type: "OAuth2",
        user: "vineetsingh5987@gmail.com",
        clientId: process.env.GOOGLE_CLIENT_ID,
        clientSecret: process.env.GOOGLE_CLIENT_SECRET,
        refreshToken: process.env.REFRESH_TOKEN,
        accessToken: accessToken,
      },
    });
}

export default transporter;