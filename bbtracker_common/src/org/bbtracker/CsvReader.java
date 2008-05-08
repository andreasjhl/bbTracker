package org.bbtracker;

import java.io.IOException;
import java.io.Reader;
import java.util.Vector;

public class CsvReader {
	private Reader reader;

	private StringBuffer buf = new StringBuffer();

	private boolean newLine;

	private boolean afterComma;

	public CsvReader(final Reader reader) {
		this.reader = reader;
	}

	public String[] nextLine() throws IOException {
		afterComma = false;
		if (reader == null) {
			return null;
		}
		final Vector v = new Vector();
		try {
			String f;
			while ((f = nextField()) != null) {
				v.add(f);
			}
		} catch (final IOException e) {
			close();
			throw e;
		}
		// reset newLine marker so that the final empty line doesn't return a new String[0]
		newLine = false;
		if (v.isEmpty() && reader == null) {
			return null;
		} else {
			final String[] result = new String[v.size()];
			v.copyInto(result);
			return result;
		}
	}

	private String nextField() throws IOException {
		if (reader == null) {
			return null;
		} else if (newLine) {
			newLine = false;
			return null;
		}
		// when the last nextField() call ended in a ',' then we return a String, even if it's empty
		boolean forceFieldEvenIfEmpty = afterComma;
		afterComma = false;
		boolean first = true;
		boolean inQuotes = false;
		boolean done = false;
		boolean doClose = false;
		int c = reader.read();
		while (!done) {
			switch (c) {
			case -1:
				if (inQuotes) {
					throw new MalformedCsvException("End of file while in quoted field!");
				}
				done = true;
				doClose = true;
				break;
			case '"':
				if (inQuotes) {
					c = reader.read();
					if (c == -1 || c == ',' || c == '\n') {
						done = true;
					} else if (c == '\r') {
						c = reader.read();
						if (c == '\n') {
							newLine = true;
							done = true;
						} else {
							throw new MalformedCsvException("Uexpected CR without LF (followed by <" + ((char) c) +
									">)");
						}
					} else if (c == '"') {
						buf.append('"');
						c = reader.read();
					} else {
						throw new MalformedCsvException("Single quote followed by <" + ((char) c) +
								"> inside quoted field!");
					}
				} else if (first) {
					inQuotes = true;
					c = reader.read();
				} else {
					throw new MalformedCsvException("Quote inside unquoted field!");
				}
				break;
			case ',':
				if (inQuotes) {
					buf.append((char) c);
					c = reader.read();
				} else {
					afterComma = true;
					forceFieldEvenIfEmpty = true;
					done = true;
				}
				break;
			case '\r':
				if (inQuotes) {
					buf.append((char) c);
					c = reader.read();
				} else {
					c = reader.read();
					if (c == '\n') {
						newLine = true;
						done = true;
					} else {
						throw new MalformedCsvException("Uexpected CR without LF (followed by <" + ((char) c) + ">)");
					}
				}
				break;
			case '\n':
				if (inQuotes) {
					buf.append((char) c);
					c = reader.read();
				} else {
					// we recognize a lone \n as a end-of-line, just as we do \r\n. Strictly speaking only the former is
					// RFC 4180 complaint
					newLine = true;
					done = true;
				}
				break;
			default:
				buf.append((char) c);
				c = reader.read();
				break;
			}
			first = false;
		}

		String result;
		if (buf.length() > 0 || forceFieldEvenIfEmpty) {
			result = buf.toString();
			buf.setLength(0);
		} else {
			result = null;
		}
		if (doClose) {
			close();
		}
		return result;
	}

	public void close() {
		if (reader == null) {
			return;
		}
		try {
			reader.close();
		} catch (final IOException e) {
			// ignore
		}
		reader = null;
		buf = null;
	}

	public static class MalformedCsvException extends IOException {
		private static final long serialVersionUID = 1L;

		public MalformedCsvException(final String msg) {
			super(msg);
		}
	}
}
