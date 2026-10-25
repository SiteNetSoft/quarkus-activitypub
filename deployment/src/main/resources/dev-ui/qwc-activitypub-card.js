import { LitElement, html, css } from 'lit';
import { JsonRpc } from 'jsonrpc';
import { pages } from 'build-time-data';
import 'qwc/qwc-extension-link.js';

/**
 * The ActivityPub card: base URL, instance actor, local actor count, delivery queue counts and paused hosts, then the
 * page links.
 */
export class QwcActivitypubCard extends LitElement {

    static styles = css`
        .identity { display: flex; flex-direction: column; gap: 0.25rem; }
        .description { padding-bottom: 0.5rem; }
        dl { display: grid; grid-template-columns: max-content auto; gap: 0.1rem 0.75rem; margin: 0 0 0.5rem 0; font-size: var(--lumo-font-size-s); }
        dt { color: var(--lumo-contrast-60pct); }
        dd { margin: 0; overflow-wrap: anywhere; }
        .error { color: var(--lumo-error-text-color); font-size: var(--lumo-font-size-s); }
    `;

    static properties = {
        description: { attribute: true },
        extensionName: { attribute: true },
        namespace: { attribute: true },
        _summary: { state: true },
        _error: { state: true },
    };

    connectedCallback() {
        super.connectedCallback();
        // the namespace attribute is only known once connected
        this.jsonRpc = new JsonRpc(this);
        this.jsonRpc.summary()
            .then(r => { this._summary = r.result; })
            .catch(e => { this._error = (e && e.error && e.error.message) || 'summary() failed'; });
    }

    render() {
        return html`
            <div class="identity">
                <span class="description">${this.description}</span>
                ${this._renderSummary()}
                ${pages.map(page => html`
                    <qwc-extension-link slot="link"
                        namespace="${this.namespace}"
                        extensionName="${this.extensionName}"
                        iconName="${page.icon}"
                        displayName="${page.title}"
                        staticLabel="${page.staticLabel}"
                        dynamicLabel="${page.dynamicLabel}"
                        streamingLabel="${page.streamingLabel}"
                        path="${page.id}"
                        ?embed=${page.embed}
                        externalUrl="${page.metadata.externalUrl}"
                        webcomponent="${page.componentLink}">
                    </qwc-extension-link>`)}
            </div>`;
    }

    _renderSummary() {
        if (this._error) {
            return html`<span class="error">${this._error}</span>`;
        }
        const s = this._summary;
        if (!s) {
            return html`<span>Loading...</span>`;
        }
        const paused = s.pausedHosts.length === 0 ? 'none' : s.pausedHosts.map(h => h.host).join(', ');
        return html`
            <dl>
                <dt>Base URL</dt><dd>${s.baseUrl}</dd>
                <dt>Instance actor</dt><dd>${s.instanceActor ?? 'disabled'}</dd>
                <dt>Local actors</dt><dd>${s.localActors}</dd>
                <dt>Deliveries</dt><dd>${s.pending} pending, ${s.failed} failed, ${s.givenUp} given up</dd>
                <dt>Paused hosts</dt><dd>${paused}</dd>
            </dl>`;
    }
}
customElements.define('qwc-activitypub-card', QwcActivitypubCard);
