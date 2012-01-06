/**
 * Author: OMAROMAN
 * Date: 11/4/11
 * Time: 11:52 AM
 */
package models;

import play.data.validation.Min;
import play.data.validation.Required;
import play.modules.recordtracking.annotations.Mask;

import javax.persistence.*;

@Entity
@Table(name = "authors")
public class Author extends AuthorAbs {

    @Mask
    @Required
    @Min(15)
    public int age;   // Just for testing purposes, the plug-in detects the field as a Primitive and uses a Wrapper and masks the value

    // Custom formatRecordTracking method
//    public String formatRecordTracking(String event) {
//        return "\n" + "YOU WILL KNOW NOTHING FROM ME, :p!!!" + "\n";
//    }

}
