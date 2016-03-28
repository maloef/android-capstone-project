package com.gmail.maloef.rememberme.persistence;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;
import android.util.Pair;

import com.gmail.maloef.rememberme.domain.Word;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.inject.Inject;

public class WordRepository {

    private Context context;
    private ContentResolver contentResolver;

    @Inject
    public WordRepository(Context context) {
        this.context = context;
        this.contentResolver = context.getContentResolver();
    }

    public int createWord(int boxId, String foreignWord, String nativeWord) {
        return createWord(boxId, 1, foreignWord, nativeWord);
    }

    public int createWord(int boxId, int compartment, String foreignWord, String nativeWord) {
        ContentValues values = new ContentValues();
        values.put(WordColumns.BOX_ID, boxId);
        values.put(WordColumns.COMPARTMENT, compartment);
        values.put(WordColumns.FOREIGN_WORD, foreignWord);
        values.put(WordColumns.NATIVE_WORD, nativeWord);
        values.put(WordColumns.CREATION_DATE, new Date().getTime());

        Uri uri = contentResolver.insert(RememberMeProvider.Word.WORDS, values);
        String lastPathSegment = uri.getLastPathSegment();
        logInfo("created word: " + values + ", uri: " + uri);

        return Integer.valueOf(lastPathSegment);
    }

    public void updateRepeatDate(int id) {
        ContentValues values = new ContentValues();
        long now = new Date().getTime();
        values.put(WordColumns.LAST_REPEAT_DATE, now);

        contentResolver.update(RememberMeProvider.Word.WORDS, values, WordColumns.ID + " = ?", new String[]{String.valueOf(id)});
    }

    public boolean doesWordExist(int boxId, String foreignWord, String nativeWord) {
        long start = System.currentTimeMillis();
        Cursor cursor = contentResolver.query(
                RememberMeProvider.Word.WORDS,
                new String[]{WordColumns.ID},
                WordColumns.BOX_ID + " = ? and " + WordColumns.FOREIGN_WORD + " = ? and " + WordColumns.NATIVE_WORD + " = ?",
                new String[]{String.valueOf(boxId), foreignWord, nativeWord},
                null);

        boolean wordExists = cursor.moveToFirst();
        cursor.close();
        long stop = System.currentTimeMillis();
        logInfo("checking if word exists took " + (stop - start) + " ms");

        return wordExists;
    }

    public Word findWord(int id) {
        WordCursor wordCursor = new WordCursor(contentResolver.query(
                RememberMeProvider.Word.WORDS,
                null,
                WordColumns.ID + " = ?",
                new String[]{String.valueOf(id)},
                null));

        Word word = wordCursor.peek();
        wordCursor.close();

        return word;
    }

    public Word getNextWord(int boxId, int compartment) {
        return getNextWord(boxId, compartment, Long.MAX_VALUE);
    }

    // ToDo 28.03.16: remove this method if not needed
    public Word getNextWord(int boxId, int compartment, long lastRepeatDateBeforeOrEqual) {
        WordCursor wordCursor = new WordCursor(contentResolver.query(
                RememberMeProvider.Word.WORDS,
                null,
                WordColumns.BOX_ID + " = ? and " + WordColumns.COMPARTMENT + " = ? and (" +
                        WordColumns.LAST_REPEAT_DATE + " is null or " + WordColumns.LAST_REPEAT_DATE + " <= ?)",
                new String[]{String.valueOf(boxId), String.valueOf(compartment), String.valueOf(lastRepeatDateBeforeOrEqual)},
                WordColumns.LAST_REPEAT_DATE + ", " + WordColumns.CREATION_DATE));

        Word word = wordCursor.moveToFirst() ? wordCursor.peek() : null;
        wordCursor.close();
        new Date(lastRepeatDateBeforeOrEqual);
        logInfo("looked up next word from box " + boxId + ", compartment " + compartment +
                " with lastRepeatDate before " + new Date(lastRepeatDateBeforeOrEqual) + ": " + word);

        return word;
    }

    public int countWords(int boxId) {
        Cursor cursor = contentResolver.query(
                RememberMeProvider.Word.WORDS,
                new String[]{WordColumns.ID},
                WordColumns.BOX_ID + " = ? ",
                new String[]{String.valueOf(boxId)},
                null);

        int result = cursor.getCount();
        cursor.close();

        return result;
    }

    public int countWords(int boxId, int compartment) {
        return countWords(boxId, compartment, Long.MAX_VALUE);
    }

    // ToDo 28.03.16: remove this method if not needed
    public int countWords(int boxId, int compartment, long lastRepeatDateBeforeOrEqual) {
        Cursor cursor = contentResolver.query(
                RememberMeProvider.Word.WORDS,
                new String[]{WordColumns.ID},
                WordColumns.BOX_ID + " = ? and " + WordColumns.COMPARTMENT + " = ? and (" +
                        WordColumns.LAST_REPEAT_DATE + " is null or " + WordColumns.LAST_REPEAT_DATE + " <= ?)",
                new String[]{String.valueOf(boxId), String.valueOf(compartment), String.valueOf(lastRepeatDateBeforeOrEqual)},
                null);

        int result = cursor.getCount();
        cursor.close();

        return result;
    }

    /**
     *
     * @param boxId
     * @param compartment
     * @param offset starts at 1, not at 0
     * @param howMany
     * @return
     */
    public List<Pair<String, String>> getWords(int boxId, int compartment, int offset, int howMany) {
        List<Pair<String, String>> words = new ArrayList<>(howMany);

        WordCursor wordCursor = new WordCursor(contentResolver.query(
                RememberMeProvider.Word.WORDS,
                new String[]{WordColumns.FOREIGN_WORD, WordColumns.NATIVE_WORD},
                WordColumns.BOX_ID + " = ? and " + WordColumns.COMPARTMENT + " = ?",
                new String[]{String.valueOf(boxId), String.valueOf(compartment)},
                WordColumns.CREATION_DATE));

        if (wordCursor.move(offset - 1)) {
            for (int i = 0; i < howMany; i++) {
                Word word = wordCursor.peek();
                words.add(Pair.create(word.foreignWord, word.nativeWord));
                if (!wordCursor.moveToNext()) {
                    break;
                };
            }
        }
        return words;
    }

    public void moveToCompartment(int wordId, int compartment) {
        ContentValues values = new ContentValues();
        values.put(WordColumns.COMPARTMENT, compartment);
        long now = new Date().getTime();
        values.put(WordColumns.LAST_REPEAT_DATE, now);

        contentResolver.update(RememberMeProvider.Word.WORDS, values, WordColumns.ID + " = ?", new String[]{String.valueOf(wordId)});
    }

    public void moveAll(int boxId, int fromCompartment, int toCompartment) {
        ContentValues values = new ContentValues();
        values.put(WordColumns.COMPARTMENT, toCompartment);
        contentResolver.update(
                RememberMeProvider.Word.WORDS,
                values,
                WordColumns.BOX_ID + " = ? and " + WordColumns.COMPARTMENT + " = ?",
                new String[]{String.valueOf(boxId), String.valueOf(fromCompartment)});
    }

    public boolean deleteWord(int wordId) {
        int deleted = contentResolver.delete(RememberMeProvider.Word.WORDS, WordColumns.ID + " = ?", new String[] {String.valueOf(wordId)});
        return deleted > 0;
    }

    void logInfo(String message) {
        Log.i(getClass().getSimpleName(), message);
    }
}
