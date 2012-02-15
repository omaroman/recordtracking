/**
 * Author: OMAROMAN
 * Date: 11/10/11
 * Time: 11:19 AM
 */

package models;

import play.data.validation.Required;
import play.data.validation.Unique;
import play.db.jpa.GenericModel;
import play.db.jpa.Model;

import javax.persistence.*;
import java.util.List;

@MappedSuperclass
public abstract class AuthorAbs extends GenericModel {

    // REVERSE ASSOCIATIONS

    @OneToMany(mappedBy = "author", cascade = CascadeType.ALL, fetch = FetchType.LAZY) // name of the variable in the other object that references this object
    public List<Quote> quotes; // = new ArrayList<Quote>(); // has_many :quotes
    @Transient private Quote quotesCastType;    // Due to type erasure, RecordTracking needs to be instructed which is the collection type

//    @OneToOne(mappedBy = "author", cascade = CascadeType.ALL, fetch = FetchType.LAZY) // name of the variable in the other object that references this object
//    public Quote quote;

    // FIELDS

    @Id
    @GeneratedValue
    public Long pk;

    @Required
    @Unique
    public String first_name;

    @Required
    public String last_name;

}
