# Invoice Spot

Welcome to the Invoice Management App repository! This application is designed to streamline the process of creating, managing, and tracking invoices for your business. It provides a user-friendly interface and a robust backend to ensure efficient invoice handling.

## Table of Contents

- [Features](#features)
- [Installation](#installation)
- [Usage](#usage)
- [Configuration](#configuration)
- [Contributing](#contributing)
- [License](#license)
- [Contact](#contact)

## Features

- **Create Invoices**: Easily create and customize invoices with client details, line items, and tax information.
- **Manage Clients**: Store and manage client information for quick access when generating invoices.
- **Track Payments**: Monitor payment statuses and history for each invoice.
- **Generate Reports**: Create comprehensive reports on invoices and payments.
- **Send Invoices**: Email invoices directly to clients from within the app.
- **Export Data**: Export invoice data in various formats such as PDF, CSV, and Excel.
- **User Authentication**: Secure login system to protect your data.
- **Password Management**: Reset and manage user passwords securely.
- **Account Management**: Update user profiles and manage user accounts.
- **Customer Management**: Create, update, and delete customer information efficiently.
- **Document Handling**: Generate, update, and delete invoices and related documents.
- **PDF Generation**: Generate PDF versions of invoices for easy sharing and printing.
- **Email Verification**: Ensure secure user registration with email verification and re-verification options.

## Installation

### Prerequisites

- [Node.js](https://nodejs.org/) (v14.x or later)
- [npm](https://www.npmjs.com/) (v6.x or later)
- [MongoDB](https://www.mongodb.com/) (v4.x or later)

### Steps

1. **Clone the repository:**
   ```bash
   git clone https://github.com/yourusername/invoicespot.git
   cd invoicespot
   ```

2. **Install backend dependencies:**
   ```bash
   npm install
   ```
3. **Set up the environment variables:**
   Create a .env file in the root directory and add the following:
   PORT=3000
   MONGODB_URI=
   JWT_SECRET=your_jwt_secret
   EMAIL_SERVICE=your_email_service
   EMAIL_USER=your_email@example.com
   EMAIL_PASS=your_email_password

5. **Start the application:**
   ```bash
   npm start
   The application will be running at http://localhost:3000.
   ```
6. **Install frontend dependencies:**
   cd client
   ```bash
   npm install
   ```
7  **.Start the application:**
   ```bash
   npm install
   ```

## Usage

### Authentication

- **Register a new user:**
  Register a new account to start using the app.

- **Login:**
  Log in with your credentials to access your dashboard.

- **Logout:**
  Log out to end your session securely.

- **Verify Email:**
  Verify your email address during registration.
  Resend verification emails if needed.

- **Password Reset:**
  Request a password reset if you've forgotten your password.
  Follow the instructions sent to your email to reset your password.

![alt text](https://github.com/Vineet829/invoicespot/blob/main/invoice-spot/images/invoice1.png)

![alt text](https://github.com/Vineet829/invoicespot/blob/main/invoice-spot/images/invoice2.png)


### Customer Management

- **Create Customer:**
  Add new customers with their contact details.

- **Update Customer Information:**
  Modify existing customer details as needed.

- **Delete Customer:**
  Remove customer entries when they are no longer needed.

- **Retrieve Customer Information:**
  View all customers or a specific customer's details.

![alt text](https://github.com/Vineet829/invoicespot/blob/main/invoice-spot/images/invoice10.png)

![alt text](https://github.com/Vineet829/invoicespot/blob/main/invoice-spot/images/invoice15.png)

![alt text](https://github.com/Vineet829/invoicespot/blob/main/invoice-spot/images/invoice16.png)


### Document Management

- **Create Document:**
  Generate new invoices and related documents.

- **Update Document:**
  Modify existing invoices with updated information.

- **Delete Document:**
  Remove invoices that are no longer needed.

- **Generate PDF:**
  Create PDF versions of invoices for easy sharing.

  ![alt text](https://github.com/Vineet829/invoicespot/blob/main/invoice-spot/images/invoice6.png)

  ![alt text](https://github.com/Vineet829/invoicespot/blob/main/invoice-spot/images/invoice7.png)

  ![alt text](https://github.com/Vineet829/invoicespot/blob/main/invoice-spot/images/invoice8.png)

  ![alt text](https://github.com/Vineet829/invoicespot/blob/main/invoice-spot/images/invoice9.png)

  ![alt text](https://github.com/Vineet829/invoicespot/blob/main/invoice-spot/images/invoice12.png)

  ![alt text](https://github.com/Vineet829/invoicespot/blob/main/invoice-spot/images/invoice13.png)


### Payment Management

- **Record Payments:**
  Record payments and track the payment status of invoices.

### User Account Management

- **View User Profile:**
  Access your user profile to view personal information.

- **Update User Profile:**
  Edit your profile details to keep them up-to-date.

- **Delete Account:**
  Delete your account if you no longer wish to use the app.

  ![alt text](https://github.com/Vineet829/invoicespot/blob/main/invoice-spot/images/invoice3.png)

  ![alt text](https://github.com/Vineet829/invoicespot/blob/main/invoice-spot/images/invoice4.png)

  ![alt text](https://github.com/Vineet829/invoicespot/blob/main/invoice-spot/images/invoice5.png)

  ![alt text](https://github.com/Vineet829/invoicespot/blob/main/invoice-spot/images/invoice1.png)


### User Management

- **User List:**
  Access and manage all registered users from a single interface.

- **Deactivate User:**
  Temporarily disable user accounts without deleting them.

- **Delete User:**
  Remove user accounts that are no longer needed.

![alt text](https://github.com/Vineet829/invoicespot/blob/main/invoice-spot/images/invoice11.png)


## Configuration

- **Database Configuration:**
  Modify the MongoDB URI in the `.env` file to connect to your database.

- **Email Service:**
  Configure the email service settings in the `.env` file for sending invoices and verification emails.

## Contributing

We welcome contributions to improve the Invoice Management App. To contribute, please follow these steps:

1. Fork the repository.
2. Create a new branch (`git checkout -b feature/your-feature-name`).
3. Make your changes.
4. Commit your changes (`git commit -m 'Add some feature'`).
5. Push to the branch (`git push origin feature/your-feature-name`).
6. Open a pull request.


## License

This project is licensed under the MIT License.

## Contact

If you have any questions or suggestions, feel free to contact me at vineetsingh5987@gmail.com.

Thank you for using the Invoice Spot.


   
