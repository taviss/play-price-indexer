package controllers;

import forms.LoginForm;
import models.User;
import models.dao.UserDAO;
import play.data.Form;
import play.data.validation.ValidationError;
import play.db.jpa.Transactional;
import play.i18n.Messages;
import play.mvc.Result;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.List;
import java.util.Map;

import static play.mvc.Controller.flash;
import static play.mvc.Controller.session;
import static play.mvc.Results.badRequest;
import static play.mvc.Results.ok;
import static play.mvc.Results.redirect;

/**
 * Created by octavian.salcianu on 7/15/2016.
 */
public class AuthorizationController {
    public static final int SALT_BYTES = 24;
    public static final int HASH_BYTES = 24;
    public static final int PBKDF2_ITERATIONS = 1000;

    /**
     * Attempts to login the user and returns ok if succes and badRequest if not
     * @return
     */
    @Transactional(readOnly = true)
    public Result tryLogin() throws NoSuchAlgorithmException, InvalidKeySpecException {
        //Form<LoginForm> form = Form.form(LoginForm.class).bindFromRequest();
        Form<User> form = Form.form(User.class).bindFromRequest();

        if (form.hasErrors()) {
            return badRequest("Invalid form");
        }
        UserDAO ud = new UserDAO();
        User loginUser = form.get();
        User foundUser = ud.getUserName(loginUser.getUserName());
        try {
            String[] params = foundUser.getUserPass().split(">");
            int iterations = Integer.parseInt(params[0]);
            byte[] salt = fromHex(params[1]);
            byte[] hash = fromHex(params[2]);
            byte[] testHash = pbkdf2(loginUser.getUserPass().toCharArray(), salt, iterations, hash.length);
            if (slowEquals(hash, testHash)) {
                session().clear();
                session("user", loginUser.getUserName());
                return ok("Logged in");
            } else {
                return badRequest("Bad combination");
            }
        } catch (NullPointerException e) {
            return badRequest("User does not exist");
        }

    }

    public Result logoutUser() {
        session().clear();
        //flash("success", "You've been logged out");
        return redirect("/");
    }

    @Transactional(readOnly = true)
    public Result registerUser() {
        Form<User> form = Form.form(User.class).bindFromRequest();

        if (form.hasErrors()) {
            return badRequest("Invalid form");
        }
        UserDAO ud = new UserDAO();
        User registerUser = form.get();
        User foundUser = ud.getUserName(registerUser.getUserName());
        if(foundUser != null) {
            return badRequest("Username in use");
        } else {
            registerUser.setUserPass(hashPassword(registerUser.getUserPass().toCharArray()));
            ud.create(registerUser);
        }
        return ok("Success");
    }

    public static String hashPassword(final char[] password) {
        try {
            byte[] salt = getSalt();

            byte[] res = pbkdf2(password, salt, PBKDF2_ITERATIONS, HASH_BYTES);
            return PBKDF2_ITERATIONS + ">" + toHex(salt) + ">" +  toHex(res);
        } catch(NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations, int bytes)
            throws NoSuchAlgorithmException, InvalidKeySpecException
    {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, bytes * 8);
        SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512");
        return skf.generateSecret(spec).getEncoded();
    }

    private static byte[] getSalt() throws NoSuchAlgorithmException {
        SecureRandom sr = SecureRandom.getInstance("SHA1PRNG");
        byte[] salt = new byte[SALT_BYTES];
        sr.nextBytes(salt);
        return salt;
    }

    private static byte[] fromHex(String hex) {
        byte[] binary = new byte[hex.length() / 2];
        for(int i = 0; i < binary.length; i++) {
            binary[i] = (byte)Integer.parseInt(hex.substring(2*i, 2*i+2), 16);
        }
        return binary;
    }

    private static String toHex(byte[] array)
    {
        BigInteger bi = new BigInteger(1, array);
        String hex = bi.toString(16);
        int paddingLength = (array.length * 2) - hex.length();
        if(paddingLength > 0)
            return String.format("%0" + paddingLength + "d", 0) + hex;
        else
            return hex;
    }

    private static boolean slowEquals(byte[] a, byte[] b)
    {
        int diff = a.length ^ b.length;
        for(int i = 0; i < a.length && i < b.length; i++)
            diff |= a[i] ^ b[i];
        return diff == 0;
    }
}