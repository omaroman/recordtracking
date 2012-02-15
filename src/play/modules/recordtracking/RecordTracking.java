/**
 * Author: OMAROMAN
 * Date: 2/13/12
 * Time: 3:04 PM
 */

package play.modules.recordtracking;

import play.db.jpa.JPA;

import javax.persistence.EntityManager;

public class RecordTracking {

    // For having another Persistence Context in order to record in PreUpdate events
    public static final EntityManager em = JPA.newEntityManager();
}
