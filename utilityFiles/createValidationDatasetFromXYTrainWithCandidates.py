import pandas as pd
import os.path

length_switch = True
max_body_length = 50
process_candidates = os.path.exists('./datasets/candidates.output')

x_train = open('./datasets/x_train').readlines()
x_train = [x.rstrip('\n') for x in x_train]
y_train = open('./datasets/y_train').readlines()
y_train = [x.rstrip('\n') for x in y_train]

x_valid = open('./datasets/x_valid').readlines()
x_valid = [x.rstrip('\n') for x in x_valid]
y_valid = open('./datasets/y_valid').readlines()
y_valid = [x.rstrip('\n') for x in y_valid]

bytecodes = open('./datasets/bytecode.output').readlines()
bytecodes = [x.rstrip('\n') for x in bytecodes]
references = open('./datasets/references.output').readlines()
references = [x.rstrip('\n') for x in references]

if (process_candidates):
    candidates = open('./datasets/candidates.output').readlines()
    candidates = [x.rstrip('\n') for x in candidates]
    df_pairs = pd.DataFrame({'source': bytecodes, 'target' : references, 'candidates': candidates })
else:
    df_pairs = pd.DataFrame({'source': bytecodes, 'target': references })

if (length_switch):
    mask = df_pairs['source'].apply(lambda x: len(x.split()) <= max_body_length)
    df_pairs = df_pairs.loc[mask]

df_train = pd.DataFrame({'source': x_train + x_valid, 'target' : y_train + y_valid })

df_valid = df_pairs.merge(df_train, on='source', indicator=True, how='left')\
    .query('_merge=="left_only"')\
    .drop('_merge', axis=1)\
    .drop('target_y', axis=1)

# df_valid = df_valid.sample(frac=1).reset_index(drop=True).sample(50000)

with open('./datasets/remaining_sources', 'w') as filehandle:
    filehandle.writelines("%s\n" % place for place in df_valid['source'])

with open('./datasets/remaining_references', 'w') as filehandle:
    filehandle.writelines("%s\n" % place for place in df_valid['target_x'])

if (process_candidates):
    with open('./datasets/remaining_candidates', 'w') as filehandle:
        filehandle.writelines("%s\n" % place for place in df_valid['candidates'])