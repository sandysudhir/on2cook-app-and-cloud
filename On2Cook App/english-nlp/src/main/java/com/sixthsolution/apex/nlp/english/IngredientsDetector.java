package com.sixthsolution.apex.nlp.english;

import com.nobigsoftware.dfalex.Pattern;
import com.sixthsolution.apex.nlp.dict.Tag;
import com.sixthsolution.apex.nlp.english.filter.PreStepDetectionFilter;
import com.sixthsolution.apex.nlp.ner.Entity;
import com.sixthsolution.apex.nlp.ner.Label;
import com.sixthsolution.apex.nlp.ner.regex.ChunkDetectionFilter;
import com.sixthsolution.apex.nlp.ner.regex.ChunkDetector;
import com.sixthsolution.apex.nlp.util.Pair;

import java.util.Arrays;
import java.util.List;

import static com.nobigsoftware.dfalex.Pattern.match;
import static com.nobigsoftware.dfalex.Pattern.regex;
import static com.sixthsolution.apex.nlp.ner.Entity.PRESTEP;

/**
 * @author Saeed Masoumi (s-masoumi@live.com)
 */

public class IngredientsDetector extends ChunkDetector {

    private static Pattern preStep() {
        return regex(".("+ Tag.PRESTEP_SUFFIX +")");
        //return match(Tag.PRESTEP_SUFFIX.toString());
    }

    @Override
    protected List<Pair<Label, Pattern>> getPatterns() {
        return Arrays.asList(
                newPattern(Label.PRESTEP, preStep())
        );
    }

    @Override
    protected List<? extends ChunkDetectionFilter> getFilters() {
        return Arrays.asList(new PreStepDetectionFilter());
    }

    @Override
    protected Entity getEntity() {
        return PRESTEP;
    }
}
