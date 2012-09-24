//----------------------------------------------------------------------------//
//                                                                            //
//                   S p i n n e r S e c t i o n M o d e l                    //
//                                                                            //
//----------------------------------------------------------------------------//
// <editor-fold defaultstate="collapsed" desc="hdr">                          //
//  Copyright © Hervé Bitteur 2000-2012. All rights reserved.                 //
//  This software is released under the GNU General Public License.           //
//  Goto http://kenai.com/projects/audiveris to report bugs or suggestions.   //
//----------------------------------------------------------------------------//
// </editor-fold>
package omr.lag.ui;

import omr.lag.Lag;

import omr.log.Logger;

import omr.ui.field.SpinnerUtilities;

import javax.swing.AbstractSpinnerModel;

/**
 * Class {@code SpinnerSectionModel} is a spinner model backed by a
 * {@link Lag}. 
 * Any modification in the lag is thus transparently handled,
 * since the lag <b>is</b> the model.
 *
 * @author Hervé Bitteur
 */
public class SpinnerSectionModel
        extends AbstractSpinnerModel
{
    //~ Static fields/initializers ---------------------------------------------

    /** Usual logger utility */
    private static final Logger logger = Logger.getLogger(
            SpinnerSectionModel.class);

    //~ Instance fields --------------------------------------------------------
    /** Underlying section lag */
    private final Lag lag;

    /** Current section id */
    private Integer currentId;

    //~ Constructors -----------------------------------------------------------
    //---------------------//
    // SpinnerSectionModel //
    //---------------------//
    /**
     * Creates a new SpinnerSectionModel object, on all lag sections
     *
     * @param lag the underlying section lag
     */
    public SpinnerSectionModel (Lag lag)
    {
        if (lag == null) {
            throw new IllegalArgumentException(
                    "SpinnerSectionModel expects non-null section lag");
        }

        this.lag = lag;

        currentId = SpinnerUtilities.NO_VALUE;
    }

    //~ Methods ----------------------------------------------------------------
    //--------------//
    // getNextValue //
    //--------------//
    /**
     * Return the next legal section id in the sequence that comes after
     * the section id returned by {@code getValue()}. 
     * If the end of the sequence has been reached then return null.
     *
     * @return the next legal section id or null if one doesn't exist
     */
    @Override
    public Object getNextValue ()
    {
        final int cur = currentId.intValue();
        logger.fine("getNextValue cur={0}", cur);

        if (cur == SpinnerUtilities.NO_VALUE) {
            return (lag.getLastVertexId() > 0) ? 1 : null;
        } else {
            return (cur < lag.getLastVertexId()) ? (cur + 1) : null;
        }
    }

    //------------------//
    // getPreviousValue //
    //------------------//
    /**
     * Return the legal section id in the sequence that comes before the
     * section id returned by {@code getValue()}. 
     * If the end of the sequence has been reached then return null.
     *
     * @return the previous legal value or null if one doesn't exist
     */
    @Override
    public Object getPreviousValue ()
    {
        final int cur = currentId.intValue();
        logger.fine("getPreviousValue cur={0}", cur);

        if (cur == SpinnerUtilities.NO_VALUE) {
            return null;
        } else {
            return (cur > 1) ? (cur - 1) : null;
        }
    }

    //----------//
    // getValue //
    //----------//
    /**
     * The <i>current element</i> of the sequence.
     *
     * @return the current spinner value.
     */
    @Override
    public Object getValue ()
    {
        logger.fine("getValue currentId={0}", currentId);

        return currentId;
    }

    //----------//
    // setValue //
    //----------//
    /**
     * Changes current section id of the model.
     * If the section id is illegal then an {@code IllegalArgumentException} is
     * thrown.
     *
     * @param value the value to set
     * @exception IllegalArgumentException if {@code value} isn't allowed
     */
    @Override
    public void setValue (Object value)
    {
        logger.fine("setValue value={0}", value);

        Integer id = (Integer) value;

        if ((id >= 0) && (id <= lag.getLastVertexId())) {
            currentId = id;
            fireStateChanged();
        } else {
            logger.warning("Invalid section id: {0}", id);
        }
    }
}