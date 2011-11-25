/**
 * Author: OMAROMAN
 * Date: 11/7/11
 * Time: 11:30 AM
 */

package controllers;

import models.Author;
import play.mvc.With;

@With(Secure.class)
@CRUD.For(Author.class)
public class AuthorsController extends CRUD  {

}
