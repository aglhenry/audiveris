//----------------------------------------------------------------------------//
//                                                                            //
//                                   C L I                                    //
//                                                                            //
//----------------------------------------------------------------------------//
// <editor-fold defaultstate="collapsed" desc="hdr">                          //
//  Copyright (C) Herve Bitteur 2000-2010. All rights reserved.               //
//  This software is released under the GNU General Public License.           //
//  Goto http://kenai.com/projects/audiveris to report bugs or suggestions.   //
//----------------------------------------------------------------------------//
// </editor-fold>
package omr;

import omr.log.Logger;

import omr.step.Step;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Class <code>CLI</code> handles the parameters of the command line interface
 *
 * @author Herv&eacute Bitteur
 */
public class CLI
{
    //~ Static fields/initializers ---------------------------------------------

    /** Usual logger utility */
    private static final Logger logger = Logger.getLogger(CLI.class);

    //~ Enumerations -----------------------------------------------------------

    /** For parameters analysis */
    private static enum Status {
        //~ Enumeration constant initializers ----------------------------------

        STEP,SHEET, SCRIPT;
    }

    //~ Instance fields --------------------------------------------------------

    /** Name of the program */
    private final String toolName;

    /** The CLI arguments */
    private final String[] args;

    /** The parameters to fill */
    private final Parameters parameters;

    //~ Constructors -----------------------------------------------------------

    //-----//
    // CLI //
    //-----//
    /**
     * Creates a new CLI object.
     *
     * @param toolName the program name
     * @param args the CLI arguments
     */
    public CLI (final String    toolName,
                final String... args)
    {
        this.toolName = toolName;
        this.args = Arrays.copyOf(args, args.length);

        parameters = parse();
    }

    //~ Methods ----------------------------------------------------------------

    //---------------//
    // getParameters //
    //---------------//
    /**
     * Parse the CLI arguments and return the populated parameters structure
     *
     * @return the parsed parameters, or null if failed
     */
    public Parameters getParameters ()
    {
        return parameters;
    }

    //----------//
    // toString //
    //----------//
    @Override
    public String toString ()
    {
        StringBuilder sb = new StringBuilder();

        for (String arg : args) {
            sb.append(" ")
              .append(arg);
        }

        return sb.toString();
    }

    //--------//
    // addRef //
    //--------//
    /**
     * Add a reference to a provided list, while handling indirections if needed
     * @param ref the reference to add, which can be a plain name (which is
     * simply added to the list) or an indirection (a name starting by the '@'
     * character) which denotes a file of references to be recursively added
     * @param list the collection of references to be augmented
     */
    private void addRef (String       ref,
                         List<String> list)
    {
        // The ref may be a plain file name or the name of a pack that lists
        // ref(s). This is signalled by a starting '@' character in ref
        if (ref.startsWith("@")) {
            // File with other refs inside
            String         pack = ref.substring(1);

            BufferedReader br = null;

            try {
                br = new BufferedReader(new FileReader(pack));

                String newRef;

                try {
                    while ((newRef = br.readLine()) != null) {
                        addRef(newRef.trim(), list);
                    }

                    br.close();
                } catch (IOException ex) {
                    logger.warning(
                        "IO error while reading file '" + pack + "'");
                }
            } catch (FileNotFoundException ex) {
                logger.warning("Cannot find file '" + pack + "'");
            } finally {
                if (br != null) {
                    try {
                        br.close();
                    } catch (Exception ignored) {
                    }
                }
            }
        } else if (ref.length() > 0) {
            // Plain file name
            list.add(ref);
        }
    }

    //-------//
    // parse //
    //-------//
    /**
     * Parse the CLI arguments and populate the parameters structure
     *
     * @return the populated parameters structure, or null if failed
     */
    private Parameters parse ()
    {
        // Status of the finite state machine
        boolean    paramNeeded = false; // Are we expecting a param?
        Status     status = Status.SHEET; // By default
        String     currentCommand = null;
        Parameters params = new Parameters();

        // Parse all arguments from command line
        for (int i = 0; i < args.length; i++) {
            String token = args[i];

            if (token.startsWith("-")) {
                // This is a command
                // Check that we were not expecting param(s)
                if (paramNeeded) {
                    printCommandLine();
                    stopUsage(
                        "Found no parameter after command '" + currentCommand +
                        "'");

                    return null;
                }

                if (token.equalsIgnoreCase("-help")) {
                    stopUsage(null);

                    return null;
                } else if (token.equalsIgnoreCase("-batch")) {
                    params.batchMode = true;
                    paramNeeded = false;
                } else if (token.equalsIgnoreCase("-step")) {
                    status = Status.STEP;
                    paramNeeded = true;
                } else if (token.equalsIgnoreCase("-sheet")) {
                    status = Status.SHEET;
                    paramNeeded = true;
                } else if (token.equalsIgnoreCase("-script")) {
                    status = Status.SCRIPT;
                    paramNeeded = true;
                } else {
                    printCommandLine();
                    stopUsage("Unknown command '" + token + "'");

                    return null;
                }

                // Remember the current command
                currentCommand = token;
            } else {
                // This is a parameter
                switch (status) {
                case STEP :

                    try {
                        // Read a step name
                        params.targetStep = Step.valueOf(token.toUpperCase());

                        // By default, sheets are now expected
                        status = Status.SHEET;
                        paramNeeded = false;
                    } catch (Exception ex) {
                        printCommandLine();
                        stopUsage(
                            "Step name expected, found '" + token +
                            "' instead");

                        return null;
                    }

                    break;

                case SHEET :
                    addRef(token, params.sheetNames);
                    paramNeeded = false;

                    break;

                case SCRIPT :
                    addRef(token, params.scriptNames);
                    paramNeeded = false;

                    break;
                }
            }
        }

        // Additional error checking
        if (paramNeeded) {
            printCommandLine();
            stopUsage(
                "Expecting a parameter after command '" + currentCommand + "'");

            return null;
        }

        // Results
        if (logger.isFineEnabled()) {
            logger.fine("CLI parameters:" + params);
        }

        return params;
    }

    //------------------//
    // printCommandLine //
    //------------------//
    /**
     * Printout the command line with its actual parameters
     */
    private void printCommandLine ()
    {
        System.err.println(toolName);
        System.err.println(this);
    }

    //-----------//
    // stopUsage //
    //-----------//
    /**
     * Printout a message if any, followed by the general syntax for the
     * command line
     * @param msg the message to print if non null
     */
    private void stopUsage (String msg)
    {
        // Print message if any
        if (msg != null) {
            logger.warning(msg);
        }

        StringBuilder buf = new StringBuilder();

        // Print standard command line syntax
        buf.append(toolName + " options syntax:")
           .append(" [-help]")
           .append(" [-batch]")
           .append(" [-step STEPNAME]")
           .append(" [-sheet (SHEETNAME|@SHEETLIST)+]")
           .append(" [-script (SCRIPTNAME|@SCRIPTLIST)+]");

        // Print all allowed step names
        buf.append("\n      Known step names are in order")
           .append(" (non case-sensitive) :");

        for (Step step : Step.values()) {
            buf.append(
                String.format(
                    "%n%-11s : %s",
                    step.toString().toUpperCase(),
                    step.description));
        }

        logger.info(buf.toString());
    }

    //~ Inner Classes ----------------------------------------------------------

    //------------//
    // Parameters //
    //------------//
    /**
     * A structure that collects the various parameters to be parsed from the
     * command line
     */
    public static class Parameters
    {
        //~ Instance fields ----------------------------------------------------

        /** Flag that indicates a batch mode */
        boolean batchMode = false;

        /** The desired step if any (option: -step stepName) */
        Step targetStep = Step.LOAD;

        /** The list of sheet file names to load */
        final List<String> sheetNames = new ArrayList<String>();

        /** The list of script file names to execute */
        final List<String> scriptNames = new ArrayList<String>();

        //~ Constructors -------------------------------------------------------

        private Parameters ()
        {
        }

        //~ Methods ------------------------------------------------------------

        @Override
        public String toString ()
        {
            StringBuilder sb = new StringBuilder();
            sb.append("\nbatchMode=")
              .append(batchMode);
            sb.append("\ntargetStep=")
              .append(targetStep);
            sb.append("\nsheetNames=")
              .append(sheetNames);
            sb.append("\nscriptNames=")
              .append(scriptNames);

            return sb.toString();
        }
    }
}
