package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import forms.LoginForm;
import forms.PasswordChangeForm;
import forms.PasswordResetForm;
import models.User;
import models.dao.UserDAO;
import play.Logger;
import play.data.Form;
import play.data.FormFactory;
import play.db.jpa.Transactional;
import play.libs.Json;
import play.mvc.*;
import java.net.MalformedURLException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.UUID;
import org.apache.commons.mail.EmailException;
import services.Mailer;
import utils.PasswordHashing;
import javax.inject.Inject;
import static utils.PasswordHashing.*;

/**
 * Created by octavian.salcianu on 7/15/2016.
 * Controller for basic account/authorization operations like login, register, password change, etc.
 */
public class AuthorizationController extends Controller {
    @Inject
    private UserDAO userDAO;

    @Inject
    private Mailer mailerClient;

    @Inject
    private FormFactory formFactory;

    /**
     * Attempts to create the user in the db if it doesn't exist and returns http responses accordingly
     * @return Result
     * @throws EmailException
     * @throws MalformedURLException
     */
    @Transactional
    @BodyParser.Of(value = BodyParser.Json.class)
    public Result registerUser() throws EmailException, MalformedURLException {
        JsonNode json = request().body().asJson();
        Form<User> form = formFactory.form(User.class).bind(json);

        if (form.hasErrors()) {
            return badRequest("Invalid form");
        }

        User registerUser = Json.fromJson(json, User.class);

        User foundUser = userDAO.getUserByName(registerUser.getUserName());
        User foundEmail = userDAO.getUserByMail(registerUser.getUserMail());

        if (foundUser != null || foundEmail != null) {
            //Log attempt
            String remote = request().remoteAddress();
            Logger.info("User register attempt:" + registerUser.getUserName() + " " + registerUser.getUserMail() + " (" + remote + ")");
            return badRequest("Username or email in use");
        } else {
            //Set the hashed password and token and send the confirmation mail + reate the user in db
            registerUser.setUserPass(hashPassword(registerUser.getUserPass().toCharArray()));
            registerUser.setUserToken(UUID.randomUUID().toString());
            registerUser.setUserActive(false);
            mailerClient.sendConfirmationMail(registerUser);
            userDAO.create(registerUser);

            //Log created user
            String remote = request().remoteAddress();
            Logger.info("User registered:" + registerUser.getUserName() + " " + registerUser.getUserMail() + " (" + remote + ")");
        }
        return ok("Success! Activate: http://localhost:9000/confirm/" + registerUser.getUserToken());
    }

    /**
     * Accessed from the email link. Given a token, it checks if the corresponding user is active or not and validates or not the account
     * @param token
     * @return Result
     */
    @Transactional
    public Result confirmUser(String token) {
        User foundUser = userDAO.getUserByToken(token);
        if (foundUser == null) {
            return badRequest("Invalid token");
        } else if (foundUser.getUserActive()) {
            return badRequest("Invalid token");
        } else {
            foundUser.setUserActive(true);
            userDAO.update(foundUser);
            return ok("Account confirmed");
        }
    }

    /**
     * Attempts to login the user and returns ok if success and badRequest if not
     * @return Result
     */
    @Transactional(readOnly = true)
    @BodyParser.Of(value = BodyParser.Json.class)
    public Result tryLogin() {
        JsonNode json = request().body().asJson();
        Form<LoginForm> form = formFactory.form(LoginForm.class).bind(json);

        if (form.hasErrors()) {
            return badRequest("Invalid form");
        }
        User foundUser = userDAO.getUserByName(form.get().userName);

        //Try to log in using form data and treat exceptions
        try {
            if (validatePassword(form.get().userPass.toCharArray(), foundUser.getUserPass())) {
                //Set the session user and log him
                session().clear();
                session("user", foundUser.getUserName());
                String remote = request().remoteAddress();
                Logger.info("User logged in: " + form.get().userName + " (" + remote + ")");
                flash("success", "You've been logged in");
                return ok("Logged in");
            } else {
                String remote = request().remoteAddress();
                Logger.info("Login attempt(BAD_PASSWORD): " + form.get().userName + " (" + remote + ")");
                return badRequest("Bad combination");
            }
        } catch (NullPointerException e) {
            String remote = request().remoteAddress();
            Logger.info("Login attempt(BAD_USERNAME): " + form.get().userName + " (" + remote + ")");
            return badRequest("User does not exist");
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            return badRequest("Internal error");
        }

    }

    /**
     * Logs the user out by clearing the session and redirects to homepage
     * @return Result(303)
     */
    @Transactional
    public Result logoutUser() {
        String remote = request().remoteAddress();
        Logger.info("User logged out: " + session().get("user") + " (" + remote + ")");
        session().clear();
        flash("success", "You've been logged out");
        return redirect("/");
    }

    /**
     * Accessed if the user is logged. Verifies if the old password is correct and if so changes the password to the new one
     * @return Result
     */
    @Security.Authenticated(Secured.class)
    @Transactional
    @BodyParser.Of(value = BodyParser.Json.class)
    public Result changeUserPassword() {
        JsonNode json = request().body().asJson();
        Form<PasswordChangeForm> form = formFactory.form(PasswordChangeForm.class).bind(json);

        if (form.hasErrors()) {
            return badRequest("Invalid form");
        }

        //Check if password repeat is the same as password
        if (!form.get().newPassword.equals(form.get().newPasswordRepeat)) {
            return badRequest("Passwords don't match");
        }

        //Try to find the user and validate the old password and change or don't change the password accordingly
        User foundUser = userDAO.getUserByName(Http.Context.current().request().username());
        try {
            if (validatePassword(form.get().oldPassword.toCharArray(), foundUser.getUserPass())) {
                foundUser.setUserPass(hashPassword(form.get().newPassword.toCharArray()));
                userDAO.update(foundUser);
                String remote = request().remoteAddress();
                Logger.info("Changed password: " + foundUser.getUserName() + " (" + remote + ")");
                return ok("Password changed");
            } else {
                String remote = request().remoteAddress();
                Logger.info("Change password attempt: " + foundUser.getUserName() + " (" + remote + ")");
                return badRequest("Wrong current password");
            }
        } catch (NullPointerException e) {
            String remote = request().remoteAddress();
            Logger.info("User tried to change password without being logged:" + remote);
            return badRequest("User does not exist");
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            return badRequest("Internal error");
        }
    }

    /**
     * Send a token by email if userName and userMail match. The token can then be used to reset password
     * @return Result
     * @throws EmailException
     * @throws MalformedURLException
     */
    @Transactional
    @BodyParser.Of(value = BodyParser.Json.class)
    public Result resetUserPassword() throws EmailException, MalformedURLException {
        JsonNode json = request().body().asJson();
        Form<PasswordResetForm> form = formFactory.form(PasswordResetForm.class).bind(json);

        if (form.hasErrors()) {
            return badRequest("Invalid form");
        }

        User foundUser = userDAO.getUserByName(form.get().userName);

        try {
            //Check if user and email are valid
            if (foundUser.getUserMail().equals(form.get().userMail)) {
                foundUser.setUserToken(UUID.randomUUID().toString());
                userDAO.update(foundUser);
                mailerClient.sendPasswordResetMail(foundUser);
                String remote = request().remoteAddress();
                Logger.info("Password reset request: " + form.get().userName + "(" + remote + ")");
                return ok("Password reset request sent");
            } else {
                String remote = request().remoteAddress();
                Logger.info("Password reset attempt: " + form.get().userName + "(" + remote + ")");
                return badRequest("User and email do not match");
            }
        } catch (NullPointerException e) {
            String remote = request().remoteAddress();
            Logger.info("Password reset attempt: " + form.get().userName + "(" + remote + ")");
            return badRequest("User does not exist");
        }
    }

    /**
     * Takes a token and sends the coresponding user a new random password by email. Resets the token afterwards
     * @param token
     * @return Result
     * @throws EmailException
     * @throws MalformedURLException
     */
    @Transactional
    public Result confirmPasswordReset(String token) throws EmailException, MalformedURLException {
        User foundUser = userDAO.getUserByToken(token);
        if (foundUser == null) {
            return badRequest("Invalid token");
        } else {
            //Set the password in plain text for the email sending
            foundUser.setUserPass(PasswordHashing.getRandomString());
            mailerClient.sendRandomPasswordMail(foundUser);
            //Now hash the password and save it
            foundUser.setUserToken(UUID.randomUUID().toString());
            foundUser.setUserPass(hashPassword(foundUser.getUserPass().toCharArray()));
            userDAO.update(foundUser);
            return ok("Password sent via email");
        }
    }
}