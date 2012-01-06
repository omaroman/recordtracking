/**
 * Author: OMAROMAN
 * Date: 11/7/11
 * Time: 11:30 AM
 */

package controllers;

import play.mvc.With;

@With(Secure.class)
@CRUD.For(models.Author.class)
public class Author extends CRUD  {


}
