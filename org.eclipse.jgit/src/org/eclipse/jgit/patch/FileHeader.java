import org.eclipse.jgit.diff.DiffEntry;
public class FileHeader extends DiffEntry {
	/**
	 * Constructs a new FileHeader
	 *
	 * @param headerLines
	 *            buffer holding the diff header for this file
	 * @param edits
	 *            the edits for this file
	 * @param type
	 *            the type of patch used to modify this file
	 */
	public FileHeader(final byte[] headerLines, EditList edits, PatchType type) {
		this(headerLines, 0);
		endOffset = headerLines.length;
		int ptr = parseGitFileName(Patch.DIFF_GIT.length, headerLines.length);
		ptr = parseGitHeaders(ptr, headerLines.length);
		this.patchType = type;
		addHunk(new HunkHeader(this, edits));
	}

					oldPath = QuotedString.GIT_PATH.dequote(buf, bol, sp - 1);
					oldPath = p1(oldPath);
					oldPath = decode(Constants.CHARSET, buf, aStart, sp - 1);
				newPath = oldPath;
				oldPath = parseName(oldPath, ptr + COPY_FROM.length, eol);
				newPath = parseName(newPath, ptr + COPY_TO.length, eol);
				oldPath = parseName(oldPath, ptr + RENAME_OLD.length, eol);
				newPath = parseName(newPath, ptr + RENAME_NEW.length, eol);
				oldPath = parseName(oldPath, ptr + RENAME_FROM.length, eol);
				newPath = parseName(newPath, ptr + RENAME_TO.length, eol);
		oldPath = p1(parseName(oldPath, ptr + OLD_NAME.length, eol));
		if (oldPath == DEV_NULL)
		newPath = p1(parseName(newPath, ptr + NEW_NAME.length, eol));
		if (newPath == DEV_NULL)