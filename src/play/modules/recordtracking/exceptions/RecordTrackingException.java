/**
 * Author: OMAROMAN
 * Date: 1/19/12
 * Time: 12:11 PM
 */

package play.modules.recordtracking.exceptions;

public class RecordTrackingException extends Exception {

    private String mistake;

    //----------------------------------------------
    // Default constructor - initializes instance variable to unknown
    public RecordTrackingException() {
        super();             // call superclass constructor
        mistake = "unknown";
    }

    //-----------------------------------------------
    // Constructor receives some kind of message that is saved in an instance variable.
    public RecordTrackingException(String err) {
        super(err);     // call super class constructor
        mistake = err;  // save message
    }

    //------------------------------------------------
    // public method, callable by exception catcher. It returns the error message.
    public String getError() {
        return mistake;
    }
}
