import static org.junit.Assert.assertEquals;
import static play.mvc.Http.Status.OK;
import org.junit.Test;
import play.inject.guice.GuiceApplicationBuilder;
import play.mvc.Result;
import play.test.WithApplication;

public class ApplicationTest extends WithApplication {

    @Override
    protected play.Application provideApplication() {
        return new GuiceApplicationBuilder()
                .configure("play.http.router", "router.Routes")
                .build();
    }

    @Test
    public void testIndex() {
        Result result = new controllers.Application().index();
        assertEquals(OK, result.status());
        assertEquals("text/html", result.contentType().get());
        assertEquals("utf-8", result.charset().get());
        //assertTrue(contentAsString(result).contains("Welcome"));
    }

}