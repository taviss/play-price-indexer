package controllers;

import models.Product;
import models.dao.ProductDAO;
import models.dao.SiteDAO;
import play.mvc.Controller;
import play.mvc.Result;
import play.db.jpa.Transactional;
import views.html.productAdd;
import views.html.index;

/**
 * Created by octavian.salcianu on 7/14/2016.
 */
public class ProductController extends Controller {

	@Transactional
	public Result addProduct(){
		ProductDAO pd = new ProductDAO();
		Product p = new Product();
		SiteDAO s = new SiteDAO();
		p.setProdName("ASUS ROG stove test");
		p.setLinkAddress("emag.ro/asus-rog-something-test");
		p.setSite(s.getSiteByKeyword("emag"));
		p = pd.create(p);
		return ok(productAdd.render(p.getId().toString(), p.getLinkAddress(), p.getProdName()));
	}


}