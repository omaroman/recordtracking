/**
 * Author: OMAROMAN
 * Date: 11/4/11
 * Time: 11:52 AM
 */
package models;

import play.data.validation.MinSize;
import play.data.validation.Required;
import play.db.jpa.Model;
import play.modules.recordtracking.annotations.Mask;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;

import play.modules.elasticsearch.annotations.ElasticSearchable;

@Entity
@Table(name = "authors")
@ElasticSearchable
public class Author extends AuthorAbs {

    @Mask
    public int years;   // Just for testing purposes, the plug-in detects the field as a Primitive and uses a Wrapper and masks the value

    // Custom formatRecordTracking method
//    public String formatRecordTracking(String event) {
//        return "\n" + "YOU WILL KNOW NOTHING FROM ME, :p!!!" + "\n";
//    }


}
