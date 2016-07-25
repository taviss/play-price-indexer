package controllers;

//import com.google.inject.Inject;
import models.User;
import play.Configuration;
import play.i18n.Messages;
import play.libs.mailer.Email;
import play.libs.mailer.MailerClient;
import org.apache.commons.mail.EmailException;
import javax.inject.Inject;
import org.apache.commons.mail.EmailAttachment;

import java.net.MalformedURLException;
import java.net.URL;


public class Mail {

    MailerClient mailerClient;

    @Inject
    public Mail(MailerClient mailer) {
        this.mailerClient = mailer;
    }

    public void sendConfirmationMail(User user) throws EmailException, MalformedURLException {
        String subject = Messages.get("mail.confirmation.subject");

        String urlString = "http://" + Configuration.root().getString("server.hostname");
        urlString += "/confirm/" + user.getUserToken();
        URL url = new URL(urlString);
        String message = Messages.get("mail.confirmation.body");
        message += ", " + url.toString();
        Email email = new Email()
                .setSubject(subject)
                .setFrom("test@gmail.com")
                .addTo(user.getUserMail())
                .setBodyText(message);
        mailerClient.send(email);
    }
}