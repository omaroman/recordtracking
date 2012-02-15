import models.Author;
import models.Quote;
import org.junit.*;
import java.util.*;
import play.test.*;
import models.*;

public class BasicTest extends UnitTest {
                                 
    @Test
    public void aVeryImportantThingToTest() {
        assertEquals(2, 1 + 1);
    }

    //@Before
    public void setup() {
        Fixtures.deleteAllModels();
        Fixtures.loadModels("initial-data.yml");
    }

    @Test
    public void testMagicTimestampFields() /*throws InvocationTargetException, NoSuchMethodException, IllegalAccessException*/ {
        Author a1 = new Author();
        a1.age = 30;
        a1.first_name = "Hombre";
        a1.last_name = "Radioactivo";
        a1.save();

        Quote q1 = new Quote();
        q1.author = a1;
        q1.quotation = "A darle átomos...";
        q1.save();

        a1.first_name = "Superman";
        a1.save();
        
        
/*        Article article = (Article) Article.findAll().get(0);
        assertNotNull(PropertyUtils.getProperty(article, "created_at"));
        assertNotNull(PropertyUtils.getProperty(article, "updated_at"));

        assertNotSame(new Date().getTime(), ((Date)PropertyUtils.getProperty(article, "created_at")).getTime());
        assertNotSame(new Date().getTime(), ((Date)PropertyUtils.getProperty(article, "updated_at")).getTime());

        assertEquals(((Date)PropertyUtils.getProperty(article, "created_at")).getTime(), ((Date)PropertyUtils.getProperty(article, "updated_at")).getTime());
        article.name = "Logitech";
        article.save();
        assertNotSame(((Date)PropertyUtils.getProperty(article, "created_at")).getTime(), ((Date)PropertyUtils.getProperty(article, "updated_at")).getTime());*/
    }

    /*@After
    public void doTestMagicTimestampFields() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        Article article = (Article) Article.findAll().get(0);

        // Since created_at and updated_at fields are injected at run time
        // they must be invoked by using reflection
        Date created_at = (Date) PropertyUtils.getProperty(article, "created_at");
        Date updated_at = (Date) PropertyUtils.getProperty(article, "updated_at");
    }*/
}
