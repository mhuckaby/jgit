/*
 * Copyright (C) 2010, Google Inc.
 * Copyright (C) 2010, Matthias Sohn <matthias.sohn@sap.com>
 * Copyright (C) 2010, Jens Baumgart <jens.baumgart@sap.com>
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.eclipse.jgit.util;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileLock;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.internal.JGitText;

/**
 * File Utilities
 */
public class FileUtils {

	/**
	 * Option to delete given {@code File}
	 */
	public static final int NONE = 0;

	/**
	 * Option to recursively delete given {@code File}
	 */
	public static final int RECURSIVE = 1;

	/**
	 * Option to retry deletion if not successful
	 */
	public static final int RETRY = 2;

	/**
	 * Option to skip deletion if file doesn't exist
	 */
	public static final int SKIP_MISSING = 4;

	/**
	 * Option not to throw exceptions when a deletion finally doesn't succeed.
	 * @since 2.0
	 */
	public static final int IGNORE_ERRORS = 8;

	/**
	 * Option to only delete empty directories. This option can be combined with
	 * {@link #RECURSIVE}
	 *
	 * @since 3.0
	 */
	public static final int EMPTY_DIRECTORIES_ONLY = 16;

	/**
	 * Delete file or empty folder
	 *
	 * @param f
	 *            {@code File} to be deleted
	 * @throws IOException
	 *             if deletion of {@code f} fails. This may occur if {@code f}
	 *             didn't exist when the method was called. This can therefore
	 *             cause IOExceptions during race conditions when multiple
	 *             concurrent threads all try to delete the same file.
	 */
	public static void delete(final File f) throws IOException {
		delete(f, NONE);
	}

	/**
	 * Delete file or folder
	 *
	 * @param f
	 *            {@code File} to be deleted
	 * @param options
	 *            deletion options, {@code RECURSIVE} for recursive deletion of
	 *            a subtree, {@code RETRY} to retry when deletion failed.
	 *            Retrying may help if the underlying file system doesn't allow
	 *            deletion of files being read by another thread.
	 * @throws IOException
	 *             if deletion of {@code f} fails. This may occur if {@code f}
	 *             didn't exist when the method was called. This can therefore
	 *             cause IOExceptions during race conditions when multiple
	 *             concurrent threads all try to delete the same file. This
	 *             exception is not thrown when IGNORE_ERRORS is set.
	 */
	public static void delete(final File f, int options) throws IOException {
		if ((options & SKIP_MISSING) != 0 && !f.exists())
			return;

		if ((options & RECURSIVE) != 0 && f.isDirectory()) {
			final File[] items = f.listFiles();
			if (items != null) {
				List<File> files = new ArrayList<File>();
				List<File> dirs = new ArrayList<File>();
				for (File c : items)
					if (c.isFile())
						files.add(c);
					else
						dirs.add(c);
				// Try to delete files first, otherwise options
				// EMPTY_DIRECTORIES_ONLY|RECURSIVE will delete empty
				// directories before aborting, depending on order.
				for (File file : files)
					delete(file, options);
				for (File d : dirs)
					delete(d, options);
			}
		}

		boolean delete = false;
		if ((options & EMPTY_DIRECTORIES_ONLY) != 0) {
			if (f.isDirectory()) {
				delete = true;
			} else {
				if ((options & IGNORE_ERRORS) == 0)
					throw new IOException(MessageFormat.format(
							JGitText.get().deleteFileFailed,
							f.getAbsolutePath()));
			}
		} else {
			delete = true;
		}

		if (delete && !f.delete()) {
			if ((options & RETRY) != 0 && f.exists()) {
				for (int i = 1; i < 10; i++) {
					try {
						Thread.sleep(100);
					} catch (InterruptedException e) {
						// ignore
					}
					if (f.delete())
						return;
				}
			}
			if ((options & IGNORE_ERRORS) == 0)
				throw new IOException(MessageFormat.format(
						JGitText.get().deleteFileFailed, f.getAbsolutePath()));
		}
	}

	/**
	 * Rename a file or folder. If the rename fails and if we are running on a
	 * filesystem where it makes sense to repeat a failing rename then repeat
	 * the rename operation up to 9 times with 100ms sleep time between two
	 * calls. Furthermore if the destination exists and is directory hierarchy
	 * with only directories in it, the whole directory hierarchy will be
	 * deleted. If the target represents a non-empty directory structure, empty
	 * subdirectories within that structure may or may not be deleted even if
	 * the method fails. Furthermore if the destination exists and is a file
	 * then the file will be deleted and then the rename is retried.
	 * <p>
	 * This operation is <em>not</me> atomic.
	 *
	 * @see FS#retryFailedLockFileCommit()
	 * @param src
	 *            the old {@code File}
	 * @param dst
	 *            the new {@code File}
	 * @throws IOException
	 *             if the rename has failed
	 * @since 3.0
	 */
	public static void rename(final File src, final File dst)
			throws IOException {
		int attempts = FS.DETECTED.retryFailedLockFileCommit() ? 10 : 1;
		while (--attempts >= 0) {
			if (src.renameTo(dst))
				return;
			try {
				if (!dst.delete())
					delete(dst, EMPTY_DIRECTORIES_ONLY | RECURSIVE);
				// On *nix there is no try, you do or do not
				if (src.renameTo(dst))
					return;
			} catch (IOException e) {
				// ignore and continue retry
			}
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				throw new IOException(MessageFormat.format(
						JGitText.get().renameFileFailed, src.getAbsolutePath(),
						dst.getAbsolutePath()));
			}
		}
		throw new IOException(MessageFormat.format(
				JGitText.get().renameFileFailed, src.getAbsolutePath(),
				dst.getAbsolutePath()));
	}

	/**
	 * Creates the directory named by this abstract pathname.
	 *
	 * @param d
	 *            directory to be created
	 * @throws IOException
	 *             if creation of {@code d} fails. This may occur if {@code d}
	 *             did exist when the method was called. This can therefore
	 *             cause IOExceptions during race conditions when multiple
	 *             concurrent threads all try to create the same directory.
	 */
	public static void mkdir(final File d)
			throws IOException {
		mkdir(d, false);
	}

	/**
	 * Creates the directory named by this abstract pathname.
	 *
	 * @param d
	 *            directory to be created
	 * @param skipExisting
	 *            if {@code true} skip creation of the given directory if it
	 *            already exists in the file system
	 * @throws IOException
	 *             if creation of {@code d} fails. This may occur if {@code d}
	 *             did exist when the method was called. This can therefore
	 *             cause IOExceptions during race conditions when multiple
	 *             concurrent threads all try to create the same directory.
	 */
	public static void mkdir(final File d, boolean skipExisting)
			throws IOException {
		if (!d.mkdir()) {
			if (skipExisting && d.isDirectory())
				return;
			throw new IOException(MessageFormat.format(
					JGitText.get().mkDirFailed, d.getAbsolutePath()));
		}
	}

	/**
	 * Creates the directory named by this abstract pathname, including any
	 * necessary but nonexistent parent directories. Note that if this operation
	 * fails it may have succeeded in creating some of the necessary parent
	 * directories.
	 *
	 * @param d
	 *            directory to be created
	 * @throws IOException
	 *             if creation of {@code d} fails. This may occur if {@code d}
	 *             did exist when the method was called. This can therefore
	 *             cause IOExceptions during race conditions when multiple
	 *             concurrent threads all try to create the same directory.
	 */
	public static void mkdirs(final File d) throws IOException {
		mkdirs(d, false);
	}

	/**
	 * Creates the directory named by this abstract pathname, including any
	 * necessary but nonexistent parent directories. Note that if this operation
	 * fails it may have succeeded in creating some of the necessary parent
	 * directories.
	 *
	 * @param d
	 *            directory to be created
	 * @param skipExisting
	 *            if {@code true} skip creation of the given directory if it
	 *            already exists in the file system
	 * @throws IOException
	 *             if creation of {@code d} fails. This may occur if {@code d}
	 *             did exist when the method was called. This can therefore
	 *             cause IOExceptions during race conditions when multiple
	 *             concurrent threads all try to create the same directory.
	 */
	public static void mkdirs(final File d, boolean skipExisting)
			throws IOException {
		if (!d.mkdirs()) {
			if (skipExisting && d.isDirectory())
				return;
			throw new IOException(MessageFormat.format(
					JGitText.get().mkDirsFailed, d.getAbsolutePath()));
		}
	}

	/**
	 * Atomically creates a new, empty file named by this abstract pathname if
	 * and only if a file with this name does not yet exist. The check for the
	 * existence of the file and the creation of the file if it does not exist
	 * are a single operation that is atomic with respect to all other
	 * filesystem activities that might affect the file.
	 * <p>
	 * Note: this method should not be used for file-locking, as the resulting
	 * protocol cannot be made to work reliably. The {@link FileLock} facility
	 * should be used instead.
	 *
	 * @param f
	 *            the file to be created
	 * @throws IOException
	 *             if the named file already exists or if an I/O error occurred
	 */
	public static void createNewFile(File f) throws IOException {
		if (!f.createNewFile())
			throw new IOException(MessageFormat.format(
					JGitText.get().createNewFileFailed, f));
	}
}
