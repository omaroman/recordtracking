/**
 * Author: OMAROMAN
 * Date: 11/10/11
 * Time: 11:19 AM
 */

package models;

import net.parnassoft.playutilities.annotations.CastType;
import play.data.validation.Required;
import play.data.validation.Unique;
import play.db.jpa.GenericModel;
import play.db.jpa.Model;
//import models.oracle.OracleModel;

import javax.persistence.*;
import java.util.List;

@MappedSuperclass
//public abstract class AuthorAbs extends OracleModel {
public abstract class AuthorAbs extends GenericModel {

    // REVERSE ASSOCIATIONS

    @OneToMany(mappedBy = "author", cascade = CascadeType.ALL, fetch = FetchType.LAZY) // name of the variable in the other object that references this object
    @CastType(type = Quote.class) // Due to type erasure, RecordTracking needs to be instructed which is the collection type
    public List<Quote> quotes; // = new ArrayList<Quote>(); // has_many :quotes

//    @OneToOne(mappedBy = "author", cascade = CascadeType.ALL, fetch = FetchType.LAZY) // name of the variable in the other object that references this object
//    public Quote quote;

    // FIELDS

    /*@Id
    @GeneratedValue
    public Long pk;*/
    @Id public Long pk;
    // NOTE: If @Id field is not named id, then write the following accessor methods
    public Long getId() {return pk;}
    public void setId(Long id) {pk = id;}

    @Required
    @Unique
    public String first_name;

    @Required
    public String last_name;

}