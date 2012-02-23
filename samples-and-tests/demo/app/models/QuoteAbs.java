/**
 * Author: OMAROMAN
 * Date: 2/23/12
 * Time: 3:31 PM
 */

package models;

import play.data.validation.Required;
import play.db.jpa.GenericModel;

import javax.persistence.*;

//@MappedSuperclass
public class QuoteAbs /*extends GenericModel*/ {

    // Associations

    @ManyToOne() // Optional, targetEntity for indicating where's the relationship
    @JoinColumn(name = "author_id") // name of the FK field in this table
    // --
    @Required
    public Author author;   // belongs_to_one :author

    // Fields

    @Id
    @GeneratedValue
    public Long id;

    @Required
    public String quotation;
}
