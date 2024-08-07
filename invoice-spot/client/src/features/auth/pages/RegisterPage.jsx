import React, { useEffect, useState } from 'react';
import LockOpenIcon from "@mui/icons-material/LockOpen";
import { Box, Button, Container, Typography, Grid, useTheme, useMediaQuery } from '@mui/material';
import { FaUserCheck } from 'react-icons/fa';
import { Link } from 'react-router-dom';
import StyledDivider from '../../../components/StyledDivider';
import AuthWrapper from '../forms/AuthWrapper';
import RegisterForm from '../forms/RegisterForm';

const RegisterPage = () => {
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('sm'));
  const [isFirefox, setIsFirefox] = useState(false);

  useEffect(() => {
    setIsFirefox(typeof InstallTrigger !== 'undefined');
  }, []);

  return (
    <AuthWrapper>
      <Container
        component="main"
        maxWidth="sm"
        sx={{
          border: '2px solid #e4e5e7',
          borderRadius: '25px',
          mt: isMobile ? 2 : 8,
          mb: isMobile ? 2 : 8,
          padding: isMobile ? 1 : 3,
          minHeight: '130vh',
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'center',
        }}
      >
        <Grid container spacing={2}>
          <Grid item xs={12}>
            <Box
              sx={{
                display: 'flex',
                flexDirection: 'row',
                justifyContent: 'center',
                alignItems: 'center',
                p: isMobile ? 1 : 3,
              }}
            >
              <FaUserCheck className="auth-svg" />
              <Typography variant={isMobile ? 'h5' : 'h1'}>Sign Up</Typography>
            </Box>
            <StyledDivider />
          </Grid>
          <Grid item xs={12}>
            <RegisterForm />
          </Grid>
          <Grid item xs={12}>
            <Box
              sx={{
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                borderRadius: '25px',
                "&:hover": {
                  bgcolor: '#CCFF996b',
                },
                mt: isFirefox && isMobile ? 4 : 2,
                mb: isFirefox && isMobile ? 4 : 2,
              }}
            >
              <Button
                startIcon={<LockOpenIcon />}
                endIcon={<LockOpenIcon />}
                sx={{
                  flexDirection: 'row',
                  textTransform: 'none',
                }}
              >
                <Typography
                  component={Link}
                  to="/login"
                  variant="h6"
                  sx={{
                    textDecoration: 'none',
                    textAlign: 'center',
                    color: 'inherit',
                  }}
                >
                  Already have an account?
                </Typography>
              </Button>
            </Box>
          </Grid>
        </Grid>
      </Container>
    </AuthWrapper>
  );
};

export default RegisterPage;
