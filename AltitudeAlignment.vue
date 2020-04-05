    <template>
<v-container>

    <v-card class="mx-auto">
        <v-card-title>Altitude Alignment</v-card-title>
        <v-container>
            <v-row xl="12">
                <input type="text" v-model="root" placeholder="select source folder/">
                <v-file-input multiple label="Image1" @change=sourcefileChanged></v-file-input>
                <v-file-input multiple label="Image2" @change=targetfileChanged></v-file-input>
            </v-row>
            <v-row xl="6">
                <input type="text" disabled placeholder="star location East/">
                <v-switch v-model="starLocation" class="mx-2" label="West   "></v-switch>
            </v-row>
            <v-row xl="6">
                <input type="text" v-model="RA" placeholder="Degrees/">
                <v-btn small @click=postInitial>Compute error</v-btn>
            </v-row>
        </v-container>
        <div class="my-2">
            <div>
                <v-row>
                    <v-col cols="4">
                        <v-container>
                            <v-text-field disabled v-model="duration" label="Duration" dense filled shaped></v-text-field>
                            <v-text-field disabled v-model="drift" label="Drift" filled dense shaped :append-icon="driftDir"></v-text-field>
                            <v-text-field disabled v-model="move" label="Move" dense filled shaped :append-icon="direction"></v-text-field>
                            <v-text-field disabled v-model="candidate" label="Candidate" filled shaped></v-text-field>
                        </v-container>
                    </v-col>
                    <v-col>
                        <v-row>
                            <v-file-input multiple label="Image1" @change=correctionfileChanged></v-file-input>
                            <v-btn small @click=postCheck>check</v-btn>
                        </v-row>
                        <v-row rows="12" sm="6">
                            <v-text-field disabled v-model="moveDelta" label="Move" filled shaped :append-icon="direction"></v-text-field>
                            <v-text-field disabled v-model="displacement" label="Displacement" filled shaped ></v-text-field>
                        </v-row>
                    </v-col>
                </v-row>
            </div>
        </div>
    </v-card>
</v-container>
</template>

<script>
import axios from 'axios';
export default {
    name: 'AltitudeAlignment',

    data: () => ({
        root: "",
        sourcefile: "",
        targetfile: "",
        starLocation: "",
        RA: undefined,
        duration:0,
        drift: 0,
        move: 0,
        candidate: "",
        gap: "",
         displacement:0,
        errors: []
    }),
    methods: {
        sourcefileChanged(event) {
            if (event && event[0]) this.sourcefile = event[0].name
        },
        targetfileChanged(event) {
            if (event && event[0]) this.targetfile = event[0].name
        },
        correctionfileChanged(event) {
            if (event && event[0]) this.correctionFile = event[0].name
        },
        postCheck() {
            var me = this
            axios.get('http://localhost:8082/alCheck?url=' + me.correctionFile)
                .then(me.praseResp)
                .catch(e => {
                    me.errors.push(e)
                })
        },
        postInitial() {
            var me = this
            var mstr = "root=" + this.root + "&img1=" + this.sourcefile + "&img2=" + this.targetfile + "&ra="+this.RA + "&starLocation="+ (this.starLocation?"W":"E")
            console.log(mstr)
            axios.get('http://localhost:8082/al?' + mstr)
                .then(me.praseResp)
                .catch(e => {
                    me.errors.push(e)
                })
        },
        praseResp() {
            arguments[0].data.split(',').forEach(a => {
                var d = a.split(':')
                this.$data[d[0]] = d[1]
                console.log(d[0] + ":" + d[1])
            })

        }
    },
    computed: {
        driftDir: function () {
            if (this.drift == 0)
                return
            return this.drift > 0 ? 'fas fa-arrow-up' : 'fas fa-arrow-down'
        },
        direction: function () {
            if (this.move == 0)
                return
            return this.move > 0 ? 'fas fa-arrow-up' : 'fas fa-arrow-down'
        },
        moveDelta: function () {
            if (this.gap == 0)
                return
            return Math.abs(this.move) - Math.abs(this.gap)
        }
    }
}
</script>
