package com.example.lab6_20221576.util;

import android.content.Context;

import com.example.lab6_20221576.R;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;

/** Traduce las excepciones de Firebase Auth a mensajes en español. */
public final class AuthErrors {
    private AuthErrors() { }

    public static String traducir(Context ctx, Exception e) {
        // OJO: WeakPassword hereda de InvalidCredentials, así que va primero.
        if (e instanceof FirebaseAuthWeakPasswordException) {
            return ctx.getString(R.string.err_weak_password);
        }
        if (e instanceof FirebaseAuthInvalidUserException) {
            return ctx.getString(R.string.err_user_not_found);
        }
        if (e instanceof FirebaseAuthInvalidCredentialsException) {
            return ctx.getString(R.string.err_bad_credentials);
        }
        if (e instanceof FirebaseAuthUserCollisionException) {
            return ctx.getString(R.string.err_user_exists);
        }
        if (e instanceof FirebaseNetworkException) {
            return ctx.getString(R.string.err_network);
        }
        return ctx.getString(R.string.err_generic);
    }
}
