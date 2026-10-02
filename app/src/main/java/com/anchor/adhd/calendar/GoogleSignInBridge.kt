@file:Suppress("DEPRECATION")

package com.anchor.adhd.calendar

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task

/**
 * Isolates legacy Google Sign-In usage until migration to Credential Manager.
 */
object GoogleSignInBridge {
    fun getLastSignedInAccount(context: Context): GoogleSignInAccount? =
        GoogleSignIn.getLastSignedInAccount(context)

    fun getSignedInAccountFromIntent(data: Intent?): Task<GoogleSignInAccount> =
        GoogleSignIn.getSignedInAccountFromIntent(data)

    fun calendarSignInClient(context: Context, calendarScope: String): GoogleSignInClient =
        GoogleSignIn.getClient(
            context,
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(Scope(calendarScope))
                .build()
        )
}
