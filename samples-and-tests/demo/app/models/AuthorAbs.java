/**
 * Author: OMAROMAN
 * Date: 11/10/11
 * Time: 11:19 AM
 */

package models;

import net.parnassoft.playutilities.annotations.Cast;
import play.data.validation.Required;
import play.data.validation.Unique;
import play.db.jpa.GenericModel;
import play.db.jpa.Model;
//import models.oracle.OracleModel;

import javax.persistence.*;
import java.util.List;
import java.util.Set;

@MappedSuperclass
public abstract class AuthorAbs extends GenericModel {

    // REVERSE ASSOCIATIONS

    @OneToMany(mappedBy = "author", cascade = CascadeType.ALL, fetch = FetchType.LAZY) // name of the variable in the other object that references this object
    @Cast(Quote.class) // Due to type erasure, RecordTracking needs to be instructed which is the collection type
    public Set<Quote> quotes; // has_many :quotes
    //public Quote quotes; // has_many :quotes

    /*@OneToOne(mappedBy = "author", cascade = CascadeType.ALL) // name of the variable in the other object that references this object
    public Quote quote;*/

    /*@OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY) // name of the variable in the other object that references this object
    @Cast(type = Quote.class) // Due to type erasure, RecordTracking needs to be instructed which is the collection type
    public List<Quote> quotes; // has_many :quotes*/

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
