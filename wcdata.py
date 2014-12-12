#!/usr/bin/env python

import os
import sys
import random

NUM_UNIQUE_WORDS=65536

if(len(sys.argv) != 2):
    sys.stderr.write('USAGE: %(p)s <num-words-to-generate>\n' % {'p':sys.argv[0]})
    sys.exit(-1)
    
def generate_basket_of_words(n):
    words=[]
    alphabet='abcedfghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ'
    for i in range(1,n):
        w=''
        for j in range(1,32):
            w = w + alphabet[random.randint(0,len(alphabet)-1)]
        words.append(w)
    return words

# Main
num_words=int(sys.argv[1])
words=generate_basket_of_words(NUM_UNIQUE_WORDS)
for i in range(0,num_words):
    print random.choice(words)

sys.exit(0)

