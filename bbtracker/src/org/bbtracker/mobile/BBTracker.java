/*
 * Copyright 2007 Joachim Sauer
 * 
 * This file is part of bbTracker.
 * 
 * bbTracker is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2 as
 * published by the Free Software Foundation.
 *
 * bbTracker is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.bbtracker.mobile;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Date;
import java.util.Timer;

import javax.microedition.io.Connector;
import javax.microedition.io.file.FileConnection;
import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.location.LocationException;
import javax.microedition.midlet.MIDlet;
import javax.microedition.midlet.MIDletStateChangeException;
import javax.microedition.rms.RecordStoreException;

import org.bbtracker.mobile.TrackStore.TrackStoreException;
import org.bbtracker.mobile.gui.MainCanvas;
import org.bbtracker.mobile.gui.OptionsForm;
import org.bbtracker.mobile.gui.TrackNameForm;
import org.bbtracker.mobile.gui.TracksForm;

public class BBTracker extends MIDlet {
	private static final String NAME = "bbTracker";

	private static String version;

	private static String fullname;

	private static BBTracker instance;

	private static PrintStream logStream;

	private final TrackManager trackManager;

	private final MainCanvas mainCanvas;

	private final Timer timer;

	private boolean firstStart = true;

	public BBTracker() {
		instance = this;

		version = getAppProperty("MIDlet-Version");
		fullname = NAME + " " + version;

		timer = new Timer();

		trackManager = new TrackManager();

		mainCanvas = new MainCanvas(trackManager);

		try {
			switch (Preferences.getInstance().getStartAction()) {
			case Preferences.START_ACTION_SHOW_OPTIONS:
			case Preferences.START_ACTION_INIT_GPS:
			case Preferences.START_ACTION_NEWTRACK:
				trackManager.initLocationProvider();
				break;
			default:
				break;
			}
		} catch (final LocationException e) {
			nonFatal(e, "Initializing Location Provider", mainCanvas);
		}

		initLog();
	}

	public void shutdown(final boolean destroy) {
		log(this, "shutdown " + destroy);
		if (trackManager != null) {
			trackManager.shutdown();
		}
		try {
			Preferences.getInstance().store();
		} catch (final RecordStoreException ignored) {
			// ignore
		}
		if (destroy) {
			notifyDestroyed();
		}
		if (logStream != null) {
			logStream.close();
			logStream = null;
		}
	}

	public static String getFullName() {
		return fullname;
	}

	public static String getName() {
		return NAME;
	}

	public static String getVersion() {
		return version;
	}

	public static BBTracker getInstance() {
		return instance;
	}

	public static Display getDisplay() {
		return Display.getDisplay(instance);
	}

	public static Timer getTimer() {
		return instance.timer;
	}

	public static void nonFatal(final Throwable t, final String action, final Displayable next) {
		log(BBTracker.class, t, "non-fatal " + action);
		final Alert alert = new Alert("Non-fatal Exception", "Non-fatal Exception while " + action + ": " +
				t.getMessage(), null, AlertType.WARNING);
		alert(alert, next);
	}

	public static void fatal(final Throwable t, final String action) {
		log(BBTracker.class, t, "fatal " + action);
		final BBTracker i = getInstance();
		i.shutdown(false);
		final Form errorForm = new Form("Fatal Exception!");
		errorForm.append("Fatal Exception while " + action + ":");
		errorForm.append(t.toString());
		errorForm.addCommand(new Command("Exit", Command.EXIT, 0));
		errorForm.setCommandListener(new CommandListener() {

			public void commandAction(final Command cmd, final Displayable displayable) {
				i.notifyDestroyed();
			}
		});
		getDisplay().setCurrent(errorForm);
	}

	public static void alert(final Alert alert, final Displayable next) {
		getDisplay().setCurrent(alert, next != null ? next : getInstance().mainCanvas);
	}

	public void showMainCanvas() {
		getDisplay().setCurrent(mainCanvas);
	}

	public static void initLog() {
		if (logStream != null) {
			return;
		}
		final String dirName = Preferences.getInstance().getTrackDirectory();
		final String logUrl = "file:///" + dirName + "debug.txt";
		try {
			final FileConnection fileConnection = (FileConnection) Connector.open(logUrl);
			if (!(fileConnection.exists() && fileConnection.canWrite())) {
				fileConnection.close();
				return;
			}
			final OutputStream out = fileConnection.openOutputStream();
			logStream = new PrintStream(out);
		} catch (final Throwable e) {
			log(BBTracker.class, e, "opening " + logUrl);
		}
	}

	public static void setLogActive(final boolean logActive) {
		if (!logActive && logStream != null) {
			logStream.close();
			logStream = null;
		}

		final String dirName = Preferences.getInstance().getTrackDirectory();
		final String logUrl = "file:///" + dirName + "debug.txt";
		try {
			final FileConnection fileConnection = (FileConnection) Connector.open(logUrl);
			if (logActive) {
				if (!fileConnection.exists()) {
					fileConnection.create();
				}
				final OutputStream out = fileConnection.openOutputStream();
				logStream = new PrintStream(out);
			} else {
				if (fileConnection.exists()) {
					fileConnection.delete();
					fileConnection.close();
				}
			}
		} catch (final Throwable e) {
			log(BBTracker.class, e, "opening " + logUrl);
		}
	}

	public static boolean isLogActive() {
		return logStream != null;
	}

	public static void log(final Object source, final Throwable e) {
		log(source, "Exception: " + e.toString());
		// this is only useful for debugging in the emulator
		e.printStackTrace();
	}

	public static void log(final Object source, final Throwable e, final String message) {
		log(source, "Exception <" + message + ">: " + e.toString());
		// this is only useful for debugging in the emulator
		e.printStackTrace();
	}

	public static void log(final Object source, final String m) {
		final String line = new Date() + ": [" + source + "] " + m;
		System.err.println(line);
		if (logStream != null) {
			logStream.println(line);
		}
	}

	protected void destroyApp(final boolean force) throws MIDletStateChangeException {
		shutdown(true);
	}

	protected void pauseApp() {
		log(this, "pauseApp");
	}

	protected void startApp() throws MIDletStateChangeException {
		log(this, firstStart ? "first startApp" : "startApp");
		if (firstStart) {
			firstStart = false;
			final int startAction = Preferences.getInstance().getStartAction();
			switch (startAction) {
			case Preferences.START_ACTION_SHOW_OPTIONS:
				Display.getDisplay(this).setCurrent(new OptionsForm(trackManager));
				break;
			case Preferences.START_ACTION_NEWTRACK:
				Display.getDisplay(this).setCurrent(new TrackNameForm(trackManager));
				break;
			case Preferences.START_ACTION_TRACKS_SCREEN:
				try {
					Display.getDisplay(this).setCurrent(new TracksForm(trackManager));
				} catch (final TrackStoreException e) {
					nonFatal(e, "Opening Track Screen", mainCanvas);
				}
				break;
			default:
				showMainCanvas();
			}
		} else {
			showMainCanvas();
		}
	}
}