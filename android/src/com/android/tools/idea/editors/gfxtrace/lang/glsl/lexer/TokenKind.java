/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.editors.gfxtrace.lang.glsl.lexer;

public enum TokenKind {
  BINARY_OP("binary operator"),
  KEYWORD("keyword"),
  NUMERIC("numeric"),
  COMMA(","),
  COMMENT("comment"),
  DOT("."),
  FALSE("false"),
  IDENTIFIER("identifier"),
  ILLEGAL("illegal character"),
  LBRACE("{"),
  LBRACKET("["),
  LPAREN("("),
  NEWLINE("newline"),
  PREPROCESSOR("preprocessor directive"),
  SPECIAL("special"),
  RBRACE("}"),
  RBRACKET("]"),
  RPAREN(")"),
  SEMI(";"),
  STRING("string"),
  TRUE("true"),
  COMPONENTS("vector or scalar components"),
  // Used for all tokens which should be ignored by the highlighter.
  WHITESPACE("whitespace");

  private final String name;

  TokenKind(String name) {
    this.name = name;
  }

  @Override
  public String toString() {
    return name;
  }
}
