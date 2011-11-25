/**
 * Author: OMAROMAN
 * Date: 11/3/11
 * Time: 10:59 AM
 */

package play.modules.recordtracking.interfaces;

import java.util.List;
import java.util.Map;

public interface Trackable {

    public String formatRecordTracking(String event);

//    public String get_track_id();
//    public Map<String, List<String>> getTrack_ids();
//    public void setTrack_ids(Map<String, List<String>> track_ids);
    public void _fill_track_data();
}
