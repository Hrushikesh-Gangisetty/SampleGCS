package com.example.aerogcsclone.authentication

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.aerogcsclone.navigation.Screen

@Composable
fun LoginPage(modifier: Modifier = Modifier,navController: NavController, authViewModel: AuthViewModel){
    var email by remember {
        mutableStateOf(value = "")
    }
    var password by remember {
        mutableStateOf(value = "")
    }
    val authState = authViewModel.authState.observeAsState()
    val context = LocalContext.current
    LaunchedEffect(authState.value) {
        when (authState.value) {
            is AuthState.Authenticated -> navController.navigate(Screen.Connection.route)
            is AuthState.Error -> Toast.makeText(context, (authState.value as AuthState.Error).message, Toast.LENGTH_SHORT).show()
            else -> Unit

        }
    }
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()),

        verticalArrangement = Arrangement.Center,

        horizontalAlignment = Alignment.CenterHorizontally

    ){

        Text(text = "Login with pavaman", fontSize = 32.sp)
        Text(text = "Login with pavaman credentials", fontSize = 12.sp)

        Spacer(modifier = Modifier.height(16.dp))



        OutlinedTextField(

            value =email ,

            onValueChange = {

                email = it

            } ,

            label = {

                Text(text = "Email")

            }

        )

        Spacer(modifier = Modifier.height(8.dp))



        OutlinedTextField(

            value =password ,

            onValueChange = {

                password = it

            } ,

            label = {

                Text(text = "Password")

            }

        )

        Spacer(modifier = Modifier.height(16.dp))



        Button(onClick = {

            authViewModel.login(email,password)



        }){

            Text(text = "Login")

        }

        Spacer(modifier = Modifier.height(8.dp))



        TextButton(onClick = {

            navController.navigate(Screen.Signup.route)



        }) {

            Text(text = "Signup")

        }



    }



}

