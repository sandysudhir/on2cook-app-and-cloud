package com.sixthsolution.apex.nlp.english;

import com.nobigsoftware.dfalex.Pattern;
import com.sixthsolution.apex.nlp.dict.Tag;
import com.sixthsolution.apex.nlp.english.filter.CookstepDetectionFilter;
import com.sixthsolution.apex.nlp.ner.Entity;
import com.sixthsolution.apex.nlp.ner.Label;
import com.sixthsolution.apex.nlp.ner.regex.ChunkDetectionFilter;
import com.sixthsolution.apex.nlp.ner.regex.ChunkDetector;
import com.sixthsolution.apex.nlp.util.Pair;

import java.util.Arrays;
import java.util.List;

import static com.nobigsoftware.dfalex.Pattern.anyOf;
import static com.nobigsoftware.dfalex.Pattern.match;
import static com.sixthsolution.apex.nlp.ner.Entity.COOKSTEP;

/**
 * @author Saeed Masoumi (s-masoumi@live.com)
 */

public class CookStepDetector extends ChunkDetector {

//    private static Pattern preStep() {
//        return match(Tag.COOKSTEP_MATCH.toString())
//                //.thenMaybe(anyOf(repeat(Tag.NONE.toString()), repeat(Tag.NUMBER.toString()), repeat(Tag.PRESTEP_SUFFIX.toString())))
//                .thenRegex(".*")
//                .then(Tag.DATE_SEPARATOR.toString());
//    }

    private static Pattern preStep() {
        return match(Tag.COOKSTEP_MATCH.toString())
                .thenMaybeRepeat(anyOf(Tag.NONE.toString(), Tag.NUMBER.toString()))
                .then(Tag.PRESTEP_SUFFIX.toString());
    }

//    private static Pattern preStep() {
//        return regex("("+Tag.COOKSTEP_MATCH.toString()+").*?("+Tag.PRESTEP_SUFFIX.toString()+")");
//    }

    private static Pattern preStep2() {
        return match(Tag.COOKSTEP_MATCH.toString())
                .thenMaybeRepeat(anyOf(match(Tag.NONE.toString()), match(Tag.NUMBER.toString())))
                .thenMaybe(Tag.TIME_MIN.toString());
    }

    @Override
    protected List<Pair<Label, Pattern>> getPatterns() {
        return Arrays.asList(
                newPattern(Label.COOKSTEP, preStep()),
                newPattern(Label.COOKSTEP, preStep2())
        );
    }

    @Override
    protected List<? extends ChunkDetectionFilter> getFilters() {
        return Arrays.asList(new CookstepDetectionFilter());
    }

    @Override
    protected Entity getEntity() {
        return COOKSTEP;
    }
}
