//----------------------------------------------------------------------------//
//                                                                            //
//                    T i m e S i g n a t u r e F i x e r                     //
//                                                                            //
//----------------------------------------------------------------------------//
// <editor-fold defaultstate="collapsed" desc="hdr">                          //
//  Copyright (C) Herve Bitteur 2000-2010. All rights reserved.               //
//  This software is released under the GNU General Public License.           //
//  Goto http://kenai.com/projects/audiveris to report bugs or suggestions.   //
//----------------------------------------------------------------------------//
// </editor-fold>
package omr.score;

import omr.log.Logger;

import omr.math.Rational;

import omr.score.entity.Measure;
import omr.score.entity.Page;
import omr.score.entity.ScoreSystem;
import omr.score.entity.Staff;
import omr.score.entity.SystemPart;
import omr.score.entity.TimeSignature;
import omr.score.entity.Voice;
import omr.score.visitor.AbstractScoreVisitor;

import omr.util.TreeNode;
import omr.util.WrappedBoolean;

import java.util.*;

/**
 * Class <code>TimeSignatureFixer</code> can visit the score hierarchy to
 * check whether each of the time signatures are consistent with most of
 * measures intrinsic time signature.
 *
 * @author Hervé Bitteur
 */
public class TimeSignatureFixer
    extends AbstractScoreVisitor
{
    //~ Static fields/initializers ---------------------------------------------

    /** Usual logger utility */
    private static final Logger logger = Logger.getLogger(
        TimeSignatureFixer.class);

    /** Used to sort integers by decreasing value */
    protected static final Comparator<Integer> reverseIntComparator = new Comparator<Integer>() {
        public int compare (Integer e1,
                            Integer e2)
        {
            return e2 - e1;
        }
    };


    //~ Instance fields --------------------------------------------------------

    /** To flag a page modification */
    private final WrappedBoolean modified;

    //~ Constructors -----------------------------------------------------------

    //--------------------//
    // TimeSignatureFixer //
    //--------------------//
    /**
     * Creates a new TimeSignatureFixer object.
     * @param modified An output boolean to signal a modification has occurred
     */
    public TimeSignatureFixer (WrappedBoolean modified)
    {
        this.modified = modified;
    }

    //~ Methods ----------------------------------------------------------------

    //------------//
    // visit Page //
    //------------//
    /**
     * Page hierarchy entry point
     *
     * @param page the page to export
     * @return false
     */
    @Override
    public boolean visit (Page page)
    {
        try {
            // We cannot rely on standard browsing part by part, since we need to 
            // address all vertical measures (of same Id), regardless of their
            // containing part
            ScoreSystem    system = page.getFirstSystem();
            SystemPart     part = system.getFirstPart();
            Measure        measure = part.getFirstMeasure();

            // Measure that starts a range of measures with an explicit time sig
            Measure        startMeasure = null;

            // Is this starting time sig a manual one?
            boolean        startManual = false;

            // Measure that ends the range
            // Right before another time sig, or last measure of the score
            Measure        stopMeasure = null;

            // Remember if current signature is manual
            // And thus should not be updated
            WrappedBoolean isManual = new WrappedBoolean(false);

            while (measure != null) {
                if (hasTimeSig(measure, isManual)) {
                    if ((startMeasure != null) && !startManual) {
                        // Complete the ongoing time sig analysis
                        checkTimeSigs(startMeasure, stopMeasure);
                    }

                    // Start a new analysis
                    startMeasure = measure;
                    startManual = isManual.isSet();
                }

                stopMeasure = measure;
                measure = measure.getFollowing();
            }

            if ((startMeasure != null) && !startManual) {
                // Complete the ongoing time sig analysis
                checkTimeSigs(startMeasure, stopMeasure);
            }
        } catch (Exception ex) {
            logger.warning(
                getClass().getSimpleName() + " Error visiting " + page,
                ex);
        }

        // Don't go the standard way (part per part)
        return false;
    }

    //---------------//
    // checkTimeSigs //
    //---------------//
    /**
     * Perform the analysis on the provided range of measures, retrieving the
     * most significant intrinsic time sig as determined by measures chords.
     * Based on this "intrinsic" time information, modify the explicit time
     * signatures accordingly.
     *
     * @param startMeasure beginning of the measure range
     * @param stopMeasure end of the measure range
     */
    private void checkTimeSigs (Measure startMeasure,
                                Measure stopMeasure)
    {
        if (logger.isFineEnabled()) {
            logger.fine(
                "checkTimeSigs on measure range " + startMeasure.getId() +
                ".." + stopMeasure.getId());
        }

        // Retrieve the best possible time signature(s)
        SortedMap<Integer, Rational> bestSigs = retrieveBestSigs(
            startMeasure,
            stopMeasure);

        if (!bestSigs.isEmpty()) {
            Rational bestRational = bestSigs.get(bestSigs.firstKey());

            if (!TimeSignature.isAcceptable(bestRational)) {
                if (logger.isFineEnabled()) {
                    logger.fine("Time sig too uncommon: " + bestRational);
                }

                return;
            }

            if (logger.isFineEnabled()) {
                logger.fine("Best sig: " + bestRational);
            }

            // Loop on every staff in the vertical startMeasure
            for (Staff.SystemIterator sit = new Staff.SystemIterator(
                startMeasure); sit.hasNext();) {
                Staff         staff = sit.next();
                Measure       measure = sit.getMeasure();
                TimeSignature sig = measure.getTimeSignature(staff);

                if (sig != null) {
                    try {
                        Rational rational = sig.getRational();

                        if (!rational.equals(bestRational)) {
                            logger.info(
                                "Measure#" + measure.getId() + " " +
                                staff.getContextString() + "T" + staff.getId() +
                                " " + rational + "->" + bestRational);

                            sig.modify(null, bestRational);
                            modified.set(true);
                        }
                    } catch (Exception ex) {
                        sig.addError(
                            sig.getGlyphs().iterator().next(),
                            "Could not check time signature " + ex);
                    }
                }
            }
        } else if (logger.isFineEnabled()) {
            logger.fine("No best sig!");
        }
    }

    //------------//
    // hasTimeSig //
    //------------//
    /**
     * Check whether the provided measure contains at least one explicit time
     * signature
     *
     * @param measure the provided measure (in fact we care only about the
     * measure id, regardless of the part)
     * @return true if a time sig exists in some staff of the measure
     */
    private boolean hasTimeSig (Measure        measure,
                                WrappedBoolean isManual)
    {
        isManual.set(false);

        boolean found = false;

        for (Staff.SystemIterator sit = new Staff.SystemIterator(measure);
             sit.hasNext();) {
            Staff         staff = sit.next();
            TimeSignature sig = sit.getMeasure()
                                   .getTimeSignature(staff);

            if (sig != null) {
                if (logger.isFineEnabled()) {
                    logger.fine(
                        "Measure#" + measure.getId() + " " +
                        staff.getContextString() + "T" + staff.getId() + " " +
                        sig);
                }

                if (sig.isManual()) {
                    isManual.set(true);
                }

                found = true;
            }
        }

        return found;
    }

    //------------------//
    // retrieveBestSigs //
    //------------------//
    /**
     * By inspecting each voice in the provided measure range, determine the
     * best intrinsic time signatures
     *
     * @param startMeasure beginning of the measure range
     * @param stopMeasure end of the measure range
     * @return a map, sorted by decreasing count, of possible time signatures
     */
    private SortedMap<Integer, Rational> retrieveBestSigs (Measure startMeasure,
                                                           Measure stopMeasure)
    {
        // Retrieve the significant measure informations
        Map<Rational, Integer> sigs = new LinkedHashMap<Rational, Integer>();
        Measure                m = startMeasure;
        int                    mIndex = m.getParent()
                                         .getChildren()
                                         .indexOf(m);

        // Loop on measure range
        while (true) {
            // Retrieve info
            if (logger.isFineEnabled()) {
                logger.fine("Checking measure#" + m.getId());
            }

            ScoreSystem system = m.getSystem();

            for (TreeNode pNode : system.getParts()) {
                SystemPart part = (SystemPart) pNode;
                Measure    measure = (Measure) part.getMeasures()
                                                   .get(mIndex);

                for (Voice voice : measure.getVoices()) {
                    Rational rational = voice.getInferredTimeSignature();

                    if (logger.isFineEnabled()) {
                        logger.fine("Voice#" + voice.getId() + ": " + rational);
                    }

                    if (rational != null) {
                        // Update histogram
                        Integer sum = sigs.get(rational);

                        if (sum == null) {
                            sum = 1;
                        } else {
                            sum += 1;
                        }

                        sigs.put(rational, sum);
                    }
                }
            }

            // Are we through?
            if (m == stopMeasure) {
                break;
            } else {
                // Move to next measure
                m = m.getFollowing();
                mIndex = m.getParent()
                          .getChildren()
                          .indexOf(m);
            }
        }

        // Sort info by decreasing counts
        SortedMap<Integer, Rational> bestSigs = new TreeMap<Integer, Rational>(
            reverseIntComparator);

        for (Map.Entry<Rational, Integer> entry : sigs.entrySet()) {
            bestSigs.put(entry.getValue(), entry.getKey());
        }

        return bestSigs;
    }
}
