import puppeteer from 'puppeteer';
import fs from "fs";
import path from "path";
import { fileURLToPath } from 'url';
import transporter from '../../helpers/emailTransport.js';
import emailTemplate from '../../utils/pdf/emailTemplate.js';
import pdfTemplate from '../../utils/pdf/pdfTemplate.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const filepath = path.join(__dirname, "../../../docs/myDocument.pdf");

const docsPath = path.join(__dirname, '../../../docs');




if (!fs.existsSync(docsPath)){
    fs.mkdirSync(docsPath, { recursive: true });
}

async function generateAndSavePDF(content) {
    const browser = await puppeteer.launch({
       
        executablePath: process.env.CHROME_BIN, args: ['--no-sandbox', '--disable-setuid-sandbox'] ,
        
    });
    const page = await browser.newPage();
    await page.setContent(pdfTemplate(content), { waitUntil: 'networkidle0' });
    await page.pdf({ path: filepath, format: 'A4' });
    await browser.close();
}

export const generatePDF = async (req, res) => {
    try {
        await generateAndSavePDF(req.body);

        res.status(200).send({ message: "PDF generated successfully." });
    } catch (error) {
        console.error(error);
        res.status(500).send({ message: "Failed to generate PDF." });
    }
};

export const getPDF = (req, res) => {
    res.sendFile(filepath);
};

export const sendDocument = async (req, res) => {
    try {
        await generateAndSavePDF(req.body);

        transporter.sendMail({
            from: process.env.SENDER_EMAIL,
            to: `${req.body.document.customer.email}`,
            replyTo: `${req.body.profile.email}`,
            subject: `Document from ${req.body.profile.businessName ? req.body.profile.businessName : req.body.profile.firstName}`,
            text: `Here is your document from ${req.body.profile.businessName ? req.body.profile.businessName : req.body.profile.firstName}`,
            html: emailTemplate(req.body),
            attachments: [{
                filename: 'myDocument.pdf',
                path: filepath,
            }],
        }, (err) => {
            if (err) {
                console.error(err);
                res.status(500).send({ message: "Failed to send the document." });
                return;
            }
            res.status(200).send({ message: "Document sent successfully." });
        });
    } catch (error) {
        console.error(error);
        res.status(500).send({ message: "Failed to send the document." });
    }
};