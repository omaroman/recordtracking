/**
 * Author: OMAROMAN
 * Date: 11/10/11
 * Time: 11:19 AM
 */

package models;

import play.data.validation.Required;
import play.db.jpa.Model;

import javax.persistence.CascadeType;
import javax.persistence.FetchType;
import javax.persistence.MappedSuperclass;
import javax.persistence.OneToMany;
import java.util.ArrayList;
import java.util.List;

@MappedSuperclass
public class AuthorAbs extends Model {

    // REVERSE ASSOCIATIONS

    @OneToMany(mappedBy = "author", cascade = CascadeType.ALL, fetch = FetchType.LAZY) // name of the variable in the other object that references this object
    public List<Quote> quotes = new ArrayList<Quote>(); // has_many :quotes

    // FIELDS

    @Required
    public String first_name;

    @Required
    public String last_name;

    // Custom formatRecordTracking method
//    public String formatRecordTracking(String event) {
//        return "\n" + "YOU WILL KNOW NOTHING FROM ME, :p!!!" + "\n";
//    }
}
